package com.wagateway.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 消息流水。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "wa_message")
public class WaMessage extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "phone_number_id", length = 64)
    private String phoneNumberId;

    @Column(name = "message_id", length = 128)
    private String messageId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    @Column(name = "to_phone", length = 32)
    private String toPhone;

    @Column(name = "from_phone", length = 32)
    private String fromPhone;

    @Column(name = "type", length = 32)
    private String type;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "queued";

    @Column(name = "payload", columnDefinition = "MEDIUMTEXT")
    private String payload;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}