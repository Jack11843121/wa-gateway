package com.wagateway.common;

/**
 * 常量定义。
 */
public final class Constants {

    private Constants() {
    }

    public static final String HEADER_API_KEY = "X-Api-Key";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_HUB_SIGNATURE = "X-Hub-Signature-256";

    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";

    public static final String MESSAGING_PRODUCT = "whatsapp";

    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    // Graph API 常用字段
    public static final String FIELD_TO = "to";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_STATUS = "status";

    // Webhook 事件类型
    public static final String WEBHOOK_MESSAGES = "messages";
    public static final String WEBHOOK_TEMPLATE_STATUS = "message_template_status_update";
    public static final String WEBHOOK_PHONE_QUALITY = "phone_number_quality_update";
    public static final String WEBHOOK_PHONE_NAME = "phone_number_name_update";
    public static final String WEBHOOK_ACCOUNT_UPDATE = "account_update";
    public static final String WEBHOOK_SECURITY = "security";
    public static final String WEBHOOK_ACCOUNT_REVIEW = "account_review_update";

    // 状态类 webhook 中 message-status 的取值
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_READ = "read";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_DELETED = "deleted";
}