package com.wagateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 动态线程池配置（Nacos 热更新）。
 * 用于下游回调 HTTP 等阻塞 IO 场景，避免占用 MQ 消费线程。
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "gateway.thread-pool")
public class ThreadPoolProperties {

    private ExecutorConfig callback = new ExecutorConfig();

    @Data
    public static class ExecutorConfig {
        private int corePoolSize = 16;
        private int maxPoolSize = 64;
        private int queueCapacity = 2000;
        private int keepAliveSeconds = 60;
    }
}