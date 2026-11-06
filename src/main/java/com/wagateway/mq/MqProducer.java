package com.wagateway.mq;

import com.wagateway.common.BizException;
import com.wagateway.common.ErrorCode;
import com.wagateway.config.GatewayProperties;
import com.wagateway.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 生产者。
 */
@Slf4j
@Component
public class MqProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final GatewayProperties props;

    public MqProducer(RocketMQTemplate rocketMQTemplate, GatewayProperties props) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.props = props;
    }

    public void sendMessage(MessageSendPayload payload) {
        String destination = props.getMq().getMessageTopic() + ":" + props.getMq().getMessageTag();
        syncSend(destination, payload);
    }

    public void sendWebhookEvent(WebhookEventPayload payload) {
        String destination = props.getMq().getWebhookTopic() + ":" + props.getMq().getWebhookTag();
        syncSend(destination, payload);
    }

    private void syncSend(String destination, Object payload) {
        try {
            String body = JsonUtil.toJson(payload);
            String key = payload instanceof MessageSendPayload p ? String.valueOf(p.getMessageRecordId())
                    : payload instanceof WebhookEventPayload w ? String.valueOf(w.getEventId()) : "";
            SendResult result = rocketMQTemplate.syncSend(destination,
                    MessageBuilder.withPayload(body).setHeader(MessageConst.PROPERTY_KEYS, key).build());
            log.debug("mq send success destination={} msgId={}", destination, result.getMsgId());
        } catch (Exception e) {
            log.error("mq send failed destination={}", destination, e);
            throw new BizException(ErrorCode.MQ_SEND_ERROR, "message queue send failed");
        }
    }
}