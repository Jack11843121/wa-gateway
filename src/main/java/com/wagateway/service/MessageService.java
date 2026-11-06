package com.wagateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.wagateway.client.GraphApiClient;
import com.wagateway.client.GraphApiException;
import com.wagateway.common.BizException;
import com.wagateway.common.Constants;
import com.wagateway.common.ErrorCode;
import com.wagateway.config.GatewayProperties;
import com.wagateway.dto.SendResponse;
import com.wagateway.enums.MessageStatuses;
import com.wagateway.mq.MqProducer;
import com.wagateway.mq.MessageSendPayload;
import com.wagateway.model.WaAccount;
import com.wagateway.model.WaMessage;
import com.wagateway.model.WaPhoneNumber;
import com.wagateway.redis.IdempotencyService;
import com.wagateway.redis.RateLimiterService;
import com.wagateway.repository.WaMessageRepository;
import com.wagateway.repository.WaPhoneNumberRepository;
import com.wagateway.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 消息发送编排：受理入队（异步）或直连发送（同步），并维护消息状态。
 */
@Slf4j
@Service
public class MessageService {

    private final WaMessageRepository messageRepository;
    private final WaPhoneNumberRepository phoneNumberRepository;
    private final AccountService accountService;
    private final GraphApiClient graphApiClient;
    private final MqProducer mqProducer;
    private final IdempotencyService idempotencyService;
    private final RateLimiterService rateLimiterService;
    private final GatewayProperties props;

    public MessageService(WaMessageRepository messageRepository,
                          WaPhoneNumberRepository phoneNumberRepository,
                          AccountService accountService,
                          GraphApiClient graphApiClient,
                          MqProducer mqProducer,
                          IdempotencyService idempotencyService,
                          RateLimiterService rateLimiterService,
                          GatewayProperties props) {
        this.messageRepository = messageRepository;
        this.phoneNumberRepository = phoneNumberRepository;
        this.accountService = accountService;
        this.graphApiClient = graphApiClient;
        this.mqProducer = mqProducer;
        this.idempotencyService = idempotencyService;
        this.rateLimiterService = rateLimiterService;
        this.props = props;
    }

    /**
     * 受理发送请求。
     *
     * @param accountId        账号
     * @param phoneNumberId    可选，为空时取账号下第一个在线号码
     * @param body             Meta 原生消息体（{messaging_product,to,type,...}）
     * @param requestId        调用方幂等键（可选）
     * @param async            异步（入队）还是同步（直连）
     */
    @Transactional
    public SendResponse send(Long accountId, String phoneNumberId, Map<String, Object> body,
                             String requestId, boolean async) {
        WaAccount account = accountService.resolve(accountId);

        if (props.getRateLimit().isEnabled()
                && !rateLimiterService.tryAcquire("wa:rate:send:" + accountId,
                props.getRateLimit().getSendPerSecond(), 1)) {
            throw new BizException(ErrorCode.RATE_LIMITED, "account send rate limit exceeded");
        }

        String resolvedPhone = resolvePhoneNumberId(accountId, phoneNumberId);
        validateBody(body);

        // 幂等
        if (StringUtils.hasText(requestId)) {
            WaMessage existing = messageRepository.findByRequestId(requestId).orElse(null);
            if (existing != null) {
                return toSendResponse(existing);
            }
            if (!idempotencyService.tryAcquire("wa:msg:req:" + requestId)) {
                // 并发防重：极小概率到达此处，回读一次
                WaMessage dup = messageRepository.findByRequestId(requestId).orElse(null);
                if (dup != null) {
                    return toSendResponse(dup);
                }
                throw new BizException(ErrorCode.BAD_REQUEST, "duplicate request id");
            }
        }

        WaMessage record = new WaMessage();
        record.setAccountId(accountId);
        record.setPhoneNumberId(resolvedPhone);
        record.setRequestId(requestId);
        record.setDirection(MessageStatuses.Direction.OUTBOUND.getValue());
        record.setToPhone(asText(body.get(Constants.FIELD_TO)));
        record.setType(asText(body.get(Constants.FIELD_TYPE)));
        record.setStatus(MessageStatuses.Outbound.QUEUED.getValue());
        record.setPayload(JsonUtil.toJson(body));
        record = messageRepository.save(record);

        if (async) {
            mqProducer.sendMessage(new MessageSendPayload(record.getId(), accountId));
            return SendResponse.builder()
                    .requestId(requestId)
                    .messageRecordId(record.getId())
                    .status(MessageStatuses.Outbound.QUEUED.getValue())
                    .accountId(accountId)
                    .phoneNumberId(resolvedPhone)
                    .build();
        }

        processSend(record.getId());
        WaMessage updated = messageRepository.findById(record.getId()).orElse(record);
        return toSendResponse(updated);
    }

    /**
     * 执行真实投递（MQ 消费入口）。
     * 业务性错误落库返回；瞬时错误抛出让 MQ 重投。
     */
    public void processSend(Long messageRecordId) {
        WaMessage record = messageRepository.findById(messageRecordId)
                .orElseThrow(() -> new BizException(ErrorCode.MESSAGE_NOT_FOUND));
        if (MessageStatuses.Outbound.SENT.getValue().equals(record.getStatus())) {
            return; // 幂等：已成功
        }

        WaAccount account = accountService.resolve(record.getAccountId());
        String version = accountService.effectiveGraphVersion(account);
        String token = accountService.plainToken(account);
        Map<String, Object> body = JsonUtil.fromJson(record.getPayload(),
                new TypeReference<Map<String, Object>>() {
                });

        try {
            JsonNode resp = graphApiClient.sendMessage(version, record.getPhoneNumberId(), body, token);
            String messageId = resp.path("messages").path(0).path("id").asText(null);
            record.setMessageId(messageId);
            record.setStatus(MessageStatuses.Outbound.SENT.getValue());
            record.setPayload(JsonUtil.toJson(body));
            record.setErrorMessage(null);
            record.setErrorCode(null);
            messageRepository.save(record);
            log.info("message sent: recordId={} wamid={}", record.getId(), messageId);
        } catch (GraphApiException e) {
            record.setStatus(MessageStatuses.Outbound.FAILED.getValue());
            record.setErrorCode(String.valueOf(e.getErrorCode()));
            record.setErrorMessage(e.getMessage());
            messageRepository.save(record);
            log.warn("message permanently failed: recordId={} code={} msg={}",
                    record.getId(), e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            // 瞬时错误（网络/5xx）：不落终态，抛出让消费者重投
            log.warn("message transient error, will retry: recordId={}", record.getId(), e);
            throw new IllegalStateException("graph api transient failure", e);
        }
    }

    @Transactional
    public void updateStatusByWamid(String wamid, String status) {
        messageRepository.findByMessageId(wamid).ifPresent(m -> {
            m.setStatus(normalizeStatus(status));
            messageRepository.save(m);
        });
    }

    /**
     * 标记已读（直连 Graph API）。
     */
    public JsonNode markRead(Long accountId, String phoneNumberId, Map<String, Object> body) {
        WaAccount account = accountService.resolve(accountId);
        String resolvedPhone = resolvePhoneNumberId(accountId, phoneNumberId);
        return graphApiClient.markMessageRead(accountService.effectiveGraphVersion(account),
                resolvedPhone, body, accountService.plainToken(account));
    }

    /**
     * 记录入站消息（按 wamid 幂等）。
     */
    @Transactional
    public void recordInboundMessage(Long accountId, String phoneNumberId, JsonNode message) {
        String messageId = message.path("id").asText(null);
        if (!StringUtils.hasText(messageId) || messageRepository.findByMessageId(messageId).isPresent()) {
            return;
        }
        WaMessage m = new WaMessage();
        m.setAccountId(accountId);
        m.setPhoneNumberId(phoneNumberId);
        m.setMessageId(messageId);
        m.setDirection(MessageStatuses.Direction.INBOUND.getValue());
        m.setFromPhone(message.path("from").asText(null));
        m.setType(message.path("type").asText(null));
        m.setStatus("received");
        m.setPayload(message.toString());
        messageRepository.save(m);
    }

    public WaMessage getByMessageId(String messageId) {
        return messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new BizException(ErrorCode.MESSAGE_NOT_FOUND));
    }

    public WaMessage getByRequestId(String requestId) {
        return messageRepository.findByRequestId(requestId)
                .orElseThrow(() -> new BizException(ErrorCode.MESSAGE_NOT_FOUND));
    }

    private String resolvePhoneNumberId(Long accountId, String phoneNumberId) {
        if (StringUtils.hasText(phoneNumberId)) {
            return phoneNumberId;
        }
        List<WaPhoneNumber> phones = phoneNumberRepository.findByAccountIdAndOnlineTrue(accountId);
        if (phones.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "no online phone number for account");
        }
        return phones.get(0).getPhoneNumberId();
    }

    private void validateBody(Map<String, Object> body) {
        if (body == null || !StringUtils.hasText(asText(body.get(Constants.FIELD_TO)))
                || !StringUtils.hasText(asText(body.get(Constants.FIELD_TYPE)))) {
            throw new BizException(ErrorCode.BAD_REQUEST, "message body must contain 'to' and 'type'");
        }
    }

    private String normalizeStatus(String status) {
        return status == null ? MessageStatuses.Outbound.FAILED.getValue() : status;
    }

    private String asText(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private SendResponse toSendResponse(WaMessage m) {
        return SendResponse.builder()
                .requestId(m.getRequestId())
                .messageRecordId(m.getId())
                .messageId(m.getMessageId())
                .status(m.getStatus())
                .accountId(m.getAccountId())
                .phoneNumberId(m.getPhoneNumberId())
                .build();
    }
}