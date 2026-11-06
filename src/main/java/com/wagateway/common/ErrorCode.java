package com.wagateway.common;

import lombok.Getter;

/**
 * 业务错误码。
 */
@Getter
public enum ErrorCode {

    OK(0, "ok"),
    BAD_REQUEST(40000, "bad request"),
    UNAUTHORIZED(40100, "unauthorized"),
    FORBIDDEN(40300, "forbidden"),
    NOT_FOUND(40400, "not found"),
    RATE_LIMITED(42900, "rate limited"),
    INTERNAL_ERROR(50000, "internal server error"),

    // 网关业务
    ACCOUNT_NOT_FOUND(41001, "whatsapp account not found"),
    ACCOUNT_DISABLED(41002, "whatsapp account disabled"),
    ACCOUNT_ALREADY_EXISTS(41003, "whatsapp account already exists"),
    INVALID_TOKEN(41004, "invalid or expired access token"),
    MESSAGE_NOT_FOUND(41005, "message not found"),
    TEMPLATE_NOT_FOUND(41006, "template not found"),
    MEDIA_NOT_FOUND(41007, "media not found"),
    WEBHOOK_SIGNATURE_INVALID(41008, "webhook signature verification failed"),
    GRAPH_API_ERROR(51001, "meta graph api error"),
    MQ_SEND_ERROR(51002, "message queue send error"),
    CALLBACK_DELIVERY_ERROR(51003, "callback delivery error");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}