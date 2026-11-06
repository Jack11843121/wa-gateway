package com.wagateway.config;

import com.wagateway.security.ApiKeyAuthFilter;
import com.wagateway.security.TraceIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 过滤器注册与顺序：traceId -> apiKey 鉴权。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> reg = new FilterRegistrationBean<>(new TraceIdFilter());
        reg.addUrlPatterns("/*");
        reg.setOrder(1);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(GatewayProperties props) {
        FilterRegistrationBean<ApiKeyAuthFilter> reg = new FilterRegistrationBean<>(new ApiKeyAuthFilter(props));
        reg.addUrlPatterns("/v1/*");
        reg.setOrder(2);
        return reg;
    }
}