package com.wagateway.service;

import com.wagateway.config.GatewayProperties;
import com.wagateway.model.WaAccount;
import com.wagateway.util.SignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 下游回调投递：将 webhook 事件按账号配置的 callback_url 转发，可选 HMAC 签名。
 * HTTP 调用在独立动态线程池（callbackExecutor）中执行，避免阻塞 MQ 消费线程。
 */
@Slf4j
@Service
public class CallbackService {

    private final RestTemplate callbackRestTemplate;
    private final ThreadPoolTaskExecutor callbackExecutor;
    private final AccountService accountService;
    private final GatewayProperties props;

    public CallbackService(AccountService accountService,
                           GatewayProperties props,
                           @Qualifier("callbackExecutor") ThreadPoolTaskExecutor callbackExecutor) {
        this.accountService = accountService;
        this.props = props;
        this.callbackExecutor = callbackExecutor;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getCallback().getTimeoutMs());
        factory.setReadTimeout(props.getCallback().getTimeoutMs());
        this.callbackRestTemplate = new RestTemplate(factory);
    }

    /**
     * @return true 投递成功（或无回调地址，视为成功）
     */
    public boolean deliver(WaAccount account, String payload) {
        if (account == null || !StringUtils.hasText(account.getCallbackUrl())) {
            return true;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Wa-Gateway-Event", "whatsapp_webhook");

        String secret = accountService.plainAppSecret(account);
        if (StringUtils.hasText(secret)) {
            headers.set("X-Wa-Gateway-Signature", "sha256="
                    + SignatureUtil.hmacSha256Hex(payload.getBytes(StandardCharsets.UTF_8), secret));
        }

        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        long timeoutMs = props.getCallback().getTimeoutMs() + 1000;
        try {
            return callbackExecutor.submit(
                    () -> callbackRestTemplate.postForEntity(account.getCallbackUrl(), entity, String.class))
                    .get(timeoutMs, TimeUnit.MILLISECONDS) != null;
        } catch (TimeoutException e) {
            log.warn("callback timeout: accountId={} url={}", account.getId(), account.getCallbackUrl());
            return false;
        } catch (Exception e) {
            log.warn("callback deliver failed: accountId={} url={} err={}",
                    account.getId(), account.getCallbackUrl(), e.getMessage());
            return false;
        }
    }
}