package com.wagateway.mq;

import com.wagateway.service.WebhookService;
import com.wagateway.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * Webhook 事件消费者：更新消息状态 + 下行回调投递。
 */
@Slf4j
@Component
// 注意：topic/group/tag 在注解中声明，与 gateway.mq.* 默认值及 Nacos 配置必须保持一致。
@RocketMQMessageListener(
        topic = "WA_WEBHOOK_EVENT",
        consumerGroup = "CG_WA_WEBHOOK_EVENT",
        selectorExpression = "EVENT_TAG")
public class WebhookEventConsumer implements RocketMQListener<String> {

    private final WebhookService webhookService;

    public WebhookEventConsumer(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @Override
    public void onMessage(String body) {
        WebhookEventPayload payload = JsonUtil.fromJson(body, WebhookEventPayload.class);
        log.info("consume webhook event eventId={}", payload.getEventId());
        webhookService.processEvent(payload.getEventId());
    }
}