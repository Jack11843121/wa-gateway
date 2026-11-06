package com.wagateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wagateway.common.BizException;
import com.wagateway.common.Constants;
import com.wagateway.common.ErrorCode;
import com.wagateway.config.GatewayProperties;
import com.wagateway.mq.MqProducer;
import com.wagateway.mq.WebhookEventPayload;
import com.wagateway.model.WaAccount;
import com.wagateway.model.WaWebhookEvent;
import com.wagateway.redis.IdempotencyService;
import com.wagateway.redis.RateLimiterService;
import com.wagateway.repository.WaWebhookEventRepository;
import com.wagateway.util.JsonUtil;
import com.wagateway.util.SignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Webhook 接收与处理：
 * 校验 → 签名验证 → 去重 → 落库 → 入队 → 消费（更新消息状态 + 下游回调）。
 */
@Slf4j
@Service
public class WebhookService {

    private final AccountService accountService;
    private final MessageService messageService;
    private final CallbackService callbackService;
    private final WaWebhookEventRepository eventRepository;
    private final MqProducer mqProducer;
    private final IdempotencyService idempotencyService;
    private final RateLimiterService rateLimiterService;
    private final GatewayProperties props;

    public WebhookService(AccountService accountService,
                          MessageService messageService,
                          CallbackService callbackService,
                          WaWebhookEventRepository eventRepository,
                          MqProducer mqProducer,
                          IdempotencyService idempotencyService,
                          RateLimiterService rateLimiterService,
                          GatewayProperties props) {
        this.accountService = accountService;
        this.messageService = messageService;
        this.callbackService = callbackService;
        this.eventRepository = eventRepository;
        this.mqProducer = mqProducer;
        this.idempotencyService = idempotencyService;
        this.rateLimiterService = rateLimiterService;
        this.props = props;
    }

    /**
     * webhook 校验（GET）。返回 challenge 原文。
     */
    public String verify(String mode, String verifyToken, String challenge) {
        if ("subscribe".equals(mode) && accountService.verifyTokenMatches(verifyToken)) {
            return challenge;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "webhook verification failed");
    }

    /**
     * 接收 webhook 事件（POST）。
     */
    @Transactional
    public void handleEvent(byte[] rawBody, String signatureHeader) {
        String body = new String(rawBody, StandardCharsets.UTF_8);
        JsonNode root;
        try {
            root = JsonUtil.toJsonNode(body);
        } catch (Exception e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "invalid webhook json");
        }

        String wabaId = extractWabaId(root);
        WaAccount account = StringUtils.hasText(wabaId) ? accountService.findByWabaId(wabaId) : null;

        // 签名校验（使用账号 app secret）
        if (props.getWebhook().isSignatureRequired()) {
            if (account == null) {
                throw new BizException(ErrorCode.FORBIDDEN, "unknown waba, cannot verify signature");
            }
            String secret = accountService.plainAppSecret(account);
            if (!StringUtils.hasText(secret) || !StringUtils.hasText(signatureHeader)) {
                throw new BizException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, "signature or secret missing");
            }
            String expected = "sha256=" + SignatureUtil.hmacSha256Hex(rawBody, secret);
            if (!SignatureUtil.constantTimeEquals(expected, signatureHeader)) {
                throw new BizException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
            }
        }

        // 去重
        String dedupKey = sha256Hex(rawBody);
        if (idempotencyService.tryAcquire("wa:webhook:dedup:" + dedupKey)) {
            if (eventRepository.existsByDedupKey(dedupKey)) {
                log.info("webhook duplicate (db hit): dedup={}", dedupKey);
                return;
            }
        } else {
            log.info("webhook duplicate (redis hit): dedup={}", dedupKey);
            return;
        }

        // 限流（可选）
        if (account != null && props.getRateLimit().isEnabled()
                && !rateLimiterService.tryAcquire("wa:rate:webhook:" + account.getId(),
                props.getRateLimit().getWebhookPerSecond(), 1)) {
            log.warn("webhook rate limited for account={}", account.getId());
            // 事件仍落库，避免丢失
        }

        String eventType = extractEventType(root);
        String messageId = extractFirstMessageId(root);
        String phoneNumberId = extractPhoneNumberId(root);

        WaWebhookEvent event = new WaWebhookEvent();
        event.setAccountId(account != null ? account.getId() : null);
        event.setWabaId(wabaId);
        event.setPhoneNumberId(phoneNumberId);
        event.setEventType(eventType);
        event.setMessageId(messageId);
        event.setDedupKey(dedupKey);
        event.setPayload(body);
        event.setStatus(1);
        event = eventRepository.save(event);

        mqProducer.sendWebhookEvent(new WebhookEventPayload(event.getId()));
    }

    /**
     * 消费 webhook 事件：更新消息状态 + 记录入站消息 + 下游回调。
     * 故意不加 @Transactional：回调失败需先落「失败态 + 重试计数」再抛出触发 MQ 重投，
     * 若包在事务里会因回滚丢失失败标记。
     */
    public void processEvent(Long eventId) {
        WaWebhookEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        if (event.getStatus() != null && event.getStatus() == 3) {
            return; // 已投递
        }

        JsonNode root = JsonUtil.toJsonNode(event.getPayload());

        // 1) 状态与入站消息解析（幂等）
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                String field = change.path("field").asText();
                if (!Constants.WEBHOOK_MESSAGES.equals(field)) {
                    continue;
                }
                JsonNode value = change.path("value");
                String phoneNumberId = value.path("metadata").path("phone_number_id").asText(null);

                for (JsonNode status : value.path("statuses")) {
                    String wamid = status.path("id").asText(null);
                    if (StringUtils.hasText(wamid)) {
                        messageService.updateStatusByWamid(wamid, status.path(Constants.FIELD_STATUS).asText());
                    }
                }
                for (JsonNode message : value.path("messages")) {
                    messageService.recordInboundMessage(event.getAccountId(), phoneNumberId, message);
                }
            }
        }

        // 2) 下游回调
        WaAccount account = event.getAccountId() != null
                ? accountService.findActiveAccount(event.getAccountId()) : null;
        boolean ok = callbackService.deliver(account, event.getPayload());

        if (ok) {
            event.setStatus(3);
            eventRepository.save(event);
        } else {
            event.setStatus(4);
            event.setRetryCount((event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1);
            eventRepository.save(event);
            // 抛出触发 RocketMQ 重投；状态更新与入站记录均为幂等
            throw new IllegalStateException("callback delivery failed, will retry");
        }
    }

    // ---------------------------------------------------------------- helpers

    private String extractWabaId(JsonNode root) {
        JsonNode entry = root.path("entry");
        if (entry.isArray() && entry.size() > 0) {
            return entry.get(0).path("id").asText(null);
        }
        return null;
    }

    private String extractEventType(JsonNode root) {
        JsonNode entry = root.path("entry");
        if (entry.isArray() && entry.size() > 0) {
            JsonNode changes = entry.get(0).path("changes");
            if (changes.isArray() && changes.size() > 0) {
                return changes.get(0).path("field").asText(null);
            }
        }
        return null;
    }

    private String extractFirstMessageId(JsonNode root) {
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                for (String key : new String[]{"messages", "statuses"}) {
                    JsonNode arr = value.path(key);
                    if (arr.isArray() && arr.size() > 0) {
                        String id = arr.get(0).path("id").asText(null);
                        if (StringUtils.hasText(id)) {
                            return id;
                        }
                    }
                }
            }
        }
        return null;
    }

    private String extractPhoneNumberId(JsonNode root) {
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                String pid = change.path("value").path("metadata").path("phone_number_id").asText(null);
                if (StringUtils.hasText(pid)) {
                    return pid;
                }
            }
        }
        return null;
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 failed", e);
        }
    }
}