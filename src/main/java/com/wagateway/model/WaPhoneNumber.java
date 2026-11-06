package com.wagateway.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 账号绑定的 WhatsApp 号码。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "wa_phone_number")
public class WaPhoneNumber extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "phone_number_id", nullable = false, length = 64)
    private String phoneNumberId;

    @Column(name = "display_phone_number", length = 32)
    private String displayPhoneNumber;

    @Column(name = "verified_name", length = 255)
    private String verifiedName;

    @Column(name = "quality_rating", length = 16)
    private String qualityRating;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "messaging_limit_tier", length = 32)
    private String messagingLimitTier;

    @Column(name = "online", nullable = false)
    private Boolean online = true;
}