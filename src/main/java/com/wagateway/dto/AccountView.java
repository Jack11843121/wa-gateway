package com.wagateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号视图（不暴露 access-token 密文）。
 */
@Data
@Builder
public class AccountView {

    private Long id;
    private String appId;
    private String wabaId;
    private String name;
    private String callbackUrl;
    private Integer status;
    private String graphVersion;
    private String timezone;
    private String currency;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}