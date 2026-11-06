package com.wagateway.client;

import lombok.Getter;

/**
 * Meta Graph API 调用异常（透传 Meta error.code / error_subcode / message）。
 */
@Getter
public class GraphApiException extends RuntimeException {

    private final int errorCode;
    private final String errorType;
    private final String fbtraceId;

    public GraphApiException(int errorCode, String errorType, String message, String fbtraceId) {
        super(message);
        this.errorCode = errorCode;
        this.errorType = errorType;
        this.fbtraceId = fbtraceId;
    }
}