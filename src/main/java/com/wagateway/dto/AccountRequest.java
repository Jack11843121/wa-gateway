package com.wagateway.dto;

import lombok.Data;

/**
 * 账号创建/更新请求。
 */
@Data
public class AccountRequest {

    /** Meta 应用 ID */
    private String appId;

    /** WhatsApp Business Account ID */
    private String wabaId;

    /** 账号别名 */
    private String name;

    /** access token（系统用户 token，落库自动加密） */
    private String accessToken;

    /** app secret（用于 webhook 签名校验） */
    private String appSecret;

    /** webhook 验证令牌 */
    private String verifyToken;

    /** 下游回调地址（webhook 事件转发目标，可空） */
    private String callbackUrl;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** Graph API 版本覆盖（可空） */
    private String graphVersion;

    private String timezone;
    private String currency;
}