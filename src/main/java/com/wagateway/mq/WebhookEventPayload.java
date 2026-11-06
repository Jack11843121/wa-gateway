package com.wagateway.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Webhook 事件处理任务（入队载荷）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEventPayload {

    private Long eventId;
}