package com.wagateway.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WhatsApp Business Account（WABA）账号。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "wa_account")
public class WaAccount extends BaseEntity {

    @Column(name = "app_id", nullable = false, length = 64)
    private String appId;

    @Column(name = "waba_id", nullable = false, length = 64)
    private String wabaId;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "access_token_enc", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String accessTokenEnc;

    @Column(name = "app_secret_enc", length = 512)
    private String appSecretEnc;

    @Column(name = "verify_token", length = 128)
    private String verifyToken;

    @Column(name = "callback_url", length = 512)
    private String callbackUrl;

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    @Column(name = "graph_version", length = 16)
    private String graphVersion;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "currency", length = 8)
    private String currency;
}