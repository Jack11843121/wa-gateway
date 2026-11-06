package com.wagateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wagateway.client.GraphApiClient;
import com.wagateway.model.WaAccount;
import com.wagateway.model.WaPhoneNumber;
import com.wagateway.repository.WaPhoneNumberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 号码管理：透传 Graph API + 本地号码缓存同步。
 */
@Slf4j
@Service
public class PhoneNumberService {

    private final AccountService accountService;
    private final GraphApiClient graphApiClient;
    private final WaPhoneNumberRepository phoneNumberRepository;

    public PhoneNumberService(AccountService accountService, GraphApiClient graphApiClient,
                              WaPhoneNumberRepository phoneNumberRepository) {
        this.accountService = accountService;
        this.graphApiClient = graphApiClient;
        this.phoneNumberRepository = phoneNumberRepository;
    }

    /**
     * 从 Meta 拉取号码并同步到本地缓存。
     */
    @Transactional
    public List<WaPhoneNumber> list(Long accountId) {
        WaAccount account = accountService.resolve(accountId);
        JsonNode resp = graphApiClient.getPhoneNumbers(accountService.effectiveGraphVersion(account),
                account.getWabaId(), accountService.plainToken(account));

        JsonNode data = resp.path("data");
        if (data.isArray()) {
            for (JsonNode node : data) {
                String phoneNumberId = node.path("id").asText(null);
                if (!StringUtils.hasText(phoneNumberId)) {
                    continue;
                }
                WaPhoneNumber pn = phoneNumberRepository.findByPhoneNumberId(phoneNumberId)
                        .orElseGet(WaPhoneNumber::new);
                pn.setAccountId(accountId);
                pn.setPhoneNumberId(phoneNumberId);
                pn.setDisplayPhoneNumber(node.path("display_phone_number").asText(null));
                pn.setVerifiedName(node.path("verified_name").asText(null));
                pn.setQualityRating(node.path("quality_rating").asText(null));
                pn.setStatus(node.path("status").asText(null));
                pn.setMessagingLimitTier(node.path("messaging_limit_tier").asText(null));
                phoneNumberRepository.save(pn);
            }
        }
        return phoneNumberRepository.findByAccountId(accountId);
    }

    public JsonNode getRemote(Long accountId, String phoneNumberId) {
        WaAccount account = accountService.resolve(accountId);
        return graphApiClient.getPhoneNumberById(accountService.effectiveGraphVersion(account),
                phoneNumberId, accountService.plainToken(account));
    }

    public JsonNode register(Long accountId, String phoneNumberId, Map<String, Object> body) {
        WaAccount account = accountService.resolve(accountId);
        return graphApiClient.registerPhone(accountService.effectiveGraphVersion(account),
                phoneNumberId, body, accountService.plainToken(account));
    }

    public JsonNode deregister(Long accountId, String phoneNumberId) {
        WaAccount account = accountService.resolve(accountId);
        return graphApiClient.deregisterPhone(accountService.effectiveGraphVersion(account),
                phoneNumberId, accountService.plainToken(account));
    }

    public JsonNode requestCode(Long accountId, String phoneNumberId, Map<String, Object> body) {
        WaAccount account = accountService.resolve(accountId);
        return graphApiClient.requestVerificationCode(accountService.effectiveGraphVersion(account),
                phoneNumberId, body, accountService.plainToken(account));
    }

    public JsonNode verifyCode(Long accountId, String phoneNumberId, Map<String, Object> body) {
        WaAccount account = accountService.resolve(accountId);
        return graphApiClient.verifyCode(accountService.effectiveGraphVersion(account),
                phoneNumberId, body, accountService.plainToken(account));
    }

    /**
     * 通用号码更新（含两步验证 pin 设置）。
     */
    public JsonNode update(Long accountId, String phoneNumberId, Map<String, Object> body) {
        WaAccount account = accountService.resolve(accountId);
        return graphApiClient.updatePhoneNumber(accountService.effectiveGraphVersion(account),
                phoneNumberId, body, accountService.plainToken(account));
    }

    public JsonNode getBusinessProfile(Long accountId, String phoneNumberId) {
        WaAccount account = accountService.resolve(accountId);
        return graphApiClient.getBusinessProfile(accountService.effectiveGraphVersion(account),
                phoneNumberId, accountService.plainToken(account));
    }

    public JsonNode updateBusinessProfile(Long accountId, String phoneNumberId, Map<String, Object> body) {
        WaAccount account = accountService.resolve(accountId);
        return graphApiClient.updateBusinessProfile(accountService.effectiveGraphVersion(account),
                phoneNumberId, body, accountService.plainToken(account));
    }
}