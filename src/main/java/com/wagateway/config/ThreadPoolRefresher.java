package com.wagateway.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 监听 Nacos 配置刷新，实时调整线程池（零第三方依赖的「动态线程池」）。
 */
@Component
public class ThreadPoolRefresher {

    private final ThreadPoolTaskExecutor callbackExecutor;

    public ThreadPoolRefresher(@Qualifier("callbackExecutor") ThreadPoolTaskExecutor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
    }

    @EventListener(RefreshScopeRefreshedEvent.class)
    public void onRefresh(ThreadPoolProperties props) {
        callbackExecutor.setCorePoolSize(props.getCallback().getCorePoolSize());
        callbackExecutor.setMaxPoolSize(props.getCallback().getMaxPoolSize());
        callbackExecutor.setKeepAliveSeconds(props.getCallback().getKeepAliveSeconds());
    }
}