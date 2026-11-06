package com.wagateway.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息发送任务（入队载荷）。
 * 只携带 ID，消费者按 DB 记录重放，避免任何敏感信息与报文进入 MQ。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageSendPayload {

    private Long messageRecordId;
    private Long accountId;
}