package com.wagateway.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Webhook 事件流水。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "wa_webhook_event")
public class WaWebhookEvent extends BaseEntity {

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "waba_id", length = 64)
    private String wabaId;

    @Column(name = "phone_number_id", length = 64)
    private String phoneNumberId;

    @Column(name = "event_type", length = 64)
    private String eventType;

    @Column(name = "message_id", length = 128)
    private String messageId;

    @Column(name = "dedup_key", nullable = false, length = 64)
    private String dedupKey;

    @Column(name = "payload", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String payload;

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;
}