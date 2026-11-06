-- =============================================================================
-- wa-gateway 数据库初始化（MySQL 8.x）
-- Flyway: classpath:db/migration
-- =============================================================================

CREATE TABLE IF NOT EXISTS wa_account (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    app_id             VARCHAR(64)  NOT NULL COMMENT 'Meta 应用 ID',
    waba_id            VARCHAR(64)  NOT NULL COMMENT 'WhatsApp Business Account ID',
    name               VARCHAR(128) NULL COMMENT '账号别名',
    access_token_enc   MEDIUMTEXT   NOT NULL COMMENT 'access token（AES-GCM 加密）',
    app_secret_enc     VARCHAR(512) NULL COMMENT 'app secret（AES-GCM 加密，用于 webhook 签名）',
    verify_token       VARCHAR(128) NULL COMMENT 'webhook 验证令牌',
    callback_url       VARCHAR(512) NULL COMMENT '下游回调地址（webhook 事件转发目标）',
    status             TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 停用',
    graph_version      VARCHAR(16)  NULL COMMENT 'Graph API 版本覆盖（为空使用全局）',
    timezone           VARCHAR(64)  NULL COMMENT '时区',
    currency           VARCHAR(8)   NULL COMMENT '币种',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_waba_id (waba_id),
    KEY idx_app_id (app_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'WhatsApp Business 账号';

CREATE TABLE IF NOT EXISTS wa_phone_number (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    account_id            BIGINT       NOT NULL COMMENT '关联 wa_account.id',
    phone_number_id       VARCHAR(64)  NOT NULL COMMENT 'Graph API 号码 ID',
    display_phone_number  VARCHAR(32)  NULL COMMENT '对外显示号码',
    verified_name         VARCHAR(255) NULL COMMENT '已验证名称',
    quality_rating        VARCHAR(16)  NULL COMMENT '质量评级',
    status                VARCHAR(32)  NULL COMMENT '号码状态',
    messaging_limit_tier  VARCHAR(32)  NULL COMMENT '消息吞吐等级',
    online                TINYINT      NOT NULL DEFAULT 1 COMMENT '是否在线可用',
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone_number_id (phone_number_id),
    KEY idx_account_id (account_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '账号绑定的 WhatsApp 号码';

CREATE TABLE IF NOT EXISTS wa_message (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    account_id      BIGINT       NOT NULL COMMENT '关联 wa_account.id',
    phone_number_id VARCHAR(64)  NULL COMMENT '发送/接收号码 ID',
    message_id      VARCHAR(128) NULL COMMENT 'Meta 消息 ID (wamid)',
    request_id      VARCHAR(64)  NULL COMMENT '调用方幂等键',
    direction       VARCHAR(16)  NOT NULL COMMENT 'outbound / inbound',
    to_phone        VARCHAR(32)  NULL COMMENT '接收方号码',
    from_phone      VARCHAR(32)  NULL COMMENT '发送方号码',
    type            VARCHAR(32)  NULL COMMENT '消息类型',
    status          VARCHAR(16)  NOT NULL DEFAULT 'queued' COMMENT '状态',
    payload         MEDIUMTEXT   NULL COMMENT '请求/响应 JSON',
    error_code      VARCHAR(64)  NULL COMMENT 'Meta 错误码',
    error_message   TEXT         NULL COMMENT '错误信息',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_id (request_id),
    KEY idx_message_id (message_id),
    KEY idx_account_status (account_id, status),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '消息流水';

CREATE TABLE IF NOT EXISTS wa_webhook_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    account_id      BIGINT       NULL COMMENT '解析后关联的 wa_account.id',
    waba_id         VARCHAR(64)  NULL COMMENT '事件所属 WABA ID',
    phone_number_id VARCHAR(64)  NULL COMMENT '事件所属号码 ID',
    event_type      VARCHAR(64)  NULL COMMENT '事件类型（messages/statuses/...）',
    message_id      VARCHAR(128) NULL COMMENT '关联消息 ID (wamid)',
    dedup_key       VARCHAR(64)  NOT NULL COMMENT '去重键（请求体 SHA-256）',
    payload         MEDIUMTEXT   NOT NULL COMMENT '原始事件 JSON',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1 已接收 2 已处理 3 已投递 4 失败',
    retry_count     INT          NOT NULL DEFAULT 0 COMMENT '回调重试次数',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dedup_key (dedup_key),
    KEY idx_waba_id (waba_id),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Webhook 事件流水';

CREATE TABLE IF NOT EXISTS wa_template (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    account_id   BIGINT       NOT NULL COMMENT '归属 WABA',
    name         VARCHAR(255) NOT NULL COMMENT '模板名称',
    language     VARCHAR(16)  NOT NULL COMMENT '语言代码',
    category     VARCHAR(32)  NULL COMMENT '分类',
    status       VARCHAR(32)  NULL COMMENT '模板状态',
    template_id  VARCHAR(128) NULL COMMENT 'Meta 模板 ID',
    components   MEDIUMTEXT   NULL COMMENT '模板组件 JSON',
    synced_at    DATETIME(3)  NULL COMMENT '最近同步时间',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_name_lang (account_id, name, language),
    KEY idx_account_id (account_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '模板本地缓存';