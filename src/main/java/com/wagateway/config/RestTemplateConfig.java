package com.wagateway.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

/**
 * Graph API 出站 RestTemplate：连接池 + 超时 + 可选固定出口代理（NAT 出口 IP 白名单）。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate graphRestTemplate(GatewayProperties props) {
        GatewayProperties.Graph g = props.getGraph();

        PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(g.getMaxTotal())
                .setMaxConnPerRoute(g.getMaxPerRoute())
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(g.getConnectTimeoutMs()))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(g.getConnectTimeoutMs()))
                .setResponseTimeout(Timeout.ofMilliseconds(g.getReadTimeoutMs()))
                .build();

        HttpClientBuilder builder = HttpClientBuilder.create()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig);

        // 可选：将 Graph API 出站流量收敛到固定 NAT 出口 IP（企业出口白名单场景）
        GatewayProperties.Outbound outbound = props.getOutbound();
        if (StringUtils.hasText(outbound.getProxyHost()) && outbound.getProxyPort() > 0) {
            builder.setProxy(new HttpHost(outbound.getProxyHost(), outbound.getProxyPort()));
        }

        CloseableHttpClient httpClient = builder.build();
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getMessageConverters().removeIf(
                c -> c instanceof StringHttpMessageConverter);
        restTemplate.getMessageConverters().add(0,
                new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate;
    }
}