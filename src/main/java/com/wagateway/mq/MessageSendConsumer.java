package com.wagateway.mq;

import com.wagateway.service.MessageService;
import com.wagateway.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 消息投递消费者：消费入队任务并调用 Meta Graph API 完成真实发送。
 */
@Slf4j
@Component
// 注意：topic/group/tag 在注解中声明，与 gateway.mq.* 默认值及 Nacos 配置必须保持一致。
// 需要改名时，请同步修改本注解、WebhookEventConsumer 与 deploy/nacos/wa-gateway-mq.yaml，并重启。
@RocketMQMessageListener(
        topic = "WA_MESSAGE_SEND",
        consumerGroup = "CG_WA_MESSAGE_SEND",
        selectorExpression = "SEND_TAG")
public class MessageSendConsumer implements RocketMQListener<String> {

    private final MessageService messageService;

    public MessageSendConsumer(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void onMessage(String body) {
        MessageSendPayload payload = JsonUtil.fromJson(body, MessageSendPayload.class);
        log.info("consume message-send task recordId={}", payload.getMessageRecordId());
        messageService.processSend(payload.getMessageRecordId());
    }
}