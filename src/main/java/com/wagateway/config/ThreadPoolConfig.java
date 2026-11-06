package com.wagateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池装配：参数由 Nacos 下发（gateway.thread-pool.*）。
 */
@Configuration
public class ThreadPoolConfig {

    @Bean("callbackExecutor")
    public ThreadPoolTaskExecutor callbackExecutor(ThreadPoolProperties props) {
        return build(props.getCallback(), "callback-");
    }

    ThreadPoolTaskExecutor build(ThreadPoolProperties.ExecutorConfig c, String prefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(c.getCorePoolSize());
        executor.setMaxPoolSize(c.getMaxPoolSize());
        executor.setQueueCapacity(c.getQueueCapacity());
        executor.setKeepAliveSeconds(c.getKeepAliveSeconds());
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}