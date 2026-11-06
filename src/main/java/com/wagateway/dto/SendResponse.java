package com.wagateway.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 异步发送受理响应。
 */
@Data
@Builder
public class SendResponse {

    /** 调用方幂等键（若有） */
    private String requestId;

    /** 网关侧消息记录主键 */
    private Long messageRecordId;

    /** Meta 消息 ID（同步模式下返回，异步受理阶段可能为空） */
    private String messageId;

    /** 受理状态 */
    private String status;

    private Long accountId;
    private String phoneNumberId;
}