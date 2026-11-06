package com.wagateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关业务配置，可在 Nacos 中按需热更新。
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Graph graph = new Graph();
    private Auth auth = new Auth();
    private Webhook webhook = new Webhook();
    private Outbound outbound = new Outbound();
    private Callback callback = new Callback();
    private Idempotent idempotent = new Idempotent();
    private Token token = new Token();
    private Mq mq = new Mq();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Graph {
        private String baseUrl = "https://graph.facebook.com";
        private String version = "v20.0";
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 20000;
        private int maxTotal = 200;
        private int maxPerRoute = 100;
    }

    @Data
    public static class Auth {
        private List<String> apiKeys = new ArrayList<>();
    }

    @Data
    public static class Webhook {
        private boolean signatureRequired = true;
        /** 应用级 webhook 验证令牌（与 Meta 应用配置一致） */
        private String verifyToken = "";
    }

    @Data
    public static class Outbound {
        private String proxyHost = "";
        private int proxyPort = 0;
    }

    @Data
    public static class Callback {
        private int maxRetry = 5;
        private long initialBackoffMs = 1000;
        private double backoffMultiplier = 2.0;
        private int timeoutMs = 4000;
    }

    @Data
    public static class Idempotent {
        private long ttlSeconds = 86400;
    }

    @Data
    public static class Token {
        private String secret = "change-me-32-char-encrypt-secret";
    }

    @Data
    public static class Mq {
        private String messageTopic = "WA_MESSAGE_SEND";
        private String messageGroup = "CG_WA_MESSAGE_SEND";
        private String messageTag = "SEND_TAG";
        private String webhookTopic = "WA_WEBHOOK_EVENT";
        private String webhookGroup = "CG_WA_WEBHOOK_EVENT";
        private String webhookTag = "EVENT_TAG";
    }

    @Data
    public static class RateLimit {
        private boolean enabled = true;
        /** 每账号每秒允许的发送请求数 */
        private int sendPerSecond = 50;
        /** 每账号每秒允许的 webhook 事件数 */
        private int webhookPerSecond = 200;
    }
}