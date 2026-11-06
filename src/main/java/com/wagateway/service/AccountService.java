package com.wagateway.service;

import com.wagateway.common.BizException;
import com.wagateway.common.ErrorCode;
import com.wagateway.config.GatewayProperties;
import com.wagateway.dto.AccountRequest;
import com.wagateway.dto.AccountView;
import com.wagateway.enums.AccountStatus;
import com.wagateway.model.WaAccount;
import com.wagateway.repository.WaAccountRepository;
import com.wagateway.util.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 账号管理 + token 加解密。
 */
@Slf4j
@Service
public class AccountService {

    private final WaAccountRepository accountRepository;
    private final GatewayProperties props;

    public AccountService(WaAccountRepository accountRepository, GatewayProperties props) {
        this.accountRepository = accountRepository;
        this.props = props;
    }

    @Transactional
    public AccountView create(AccountRequest req) {
        if (!StringUtils.hasText(req.getWabaId()) || !StringUtils.hasText(req.getAppId())
                || !StringUtils.hasText(req.getAccessToken())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "appId/wabaId/accessToken are required");
        }
        if (accountRepository.existsByWabaId(req.getWabaId())) {
            throw new BizException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
        WaAccount account = new WaAccount();
        applyRequest(account, req);
        account.setAccessTokenEnc(AesUtil.encrypt(req.getAccessToken(), props.getToken().getSecret()));
        account.setStatus(AccountStatus.ACTIVE.getValue());
        return toView(accountRepository.save(account));
    }

    @Transactional
    public AccountView update(Long id, AccountRequest req) {
        WaAccount account = resolve(id);
        applyRequest(account, req);
        if (StringUtils.hasText(req.getAccessToken())) {
            account.setAccessTokenEnc(AesUtil.encrypt(req.getAccessToken(), props.getToken().getSecret()));
        }
        return toView(accountRepository.save(account));
    }

    @Transactional
    public void delete(Long id) {
        WaAccount account = resolve(id);
        account.setStatus(AccountStatus.DISABLED.getValue());
        accountRepository.save(account);
    }

    public AccountView get(Long id) {
        return toView(resolve(id));
    }

    public List<AccountView> list() {
        return accountRepository.findAllByStatusOrderByIdDesc(AccountStatus.ACTIVE.getValue())
                .stream().map(this::toView).toList();
    }

    public WaAccount resolve(Long id) {
        WaAccount account = accountRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (account.getStatus() == AccountStatus.DISABLED.getValue()) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        return account;
    }

    public WaAccount findByWabaId(String wabaId) {
        return accountRepository.findByWabaId(wabaId).orElse(null);
    }

    public WaAccount findActiveAccount(Long id) {
        return accountRepository.findById(id)
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE.getValue())
                .orElse(null);
    }

    public String plainToken(WaAccount account) {
        return AesUtil.decrypt(account.getAccessTokenEnc(), props.getToken().getSecret());
    }

    public String plainAppSecret(WaAccount account) {
        if (!StringUtils.hasText(account.getAppSecretEnc())) {
            return null;
        }
        return AesUtil.decrypt(account.getAppSecretEnc(), props.getToken().getSecret());
    }

    /**
     * webhook 校验：匹配网关级验证令牌或任一账号的 verify_token。
     */
    public boolean verifyTokenMatches(String token) {
        if (StringUtils.hasText(props.getWebhook().getVerifyToken())
                && props.getWebhook().getVerifyToken().equals(token)) {
            return true;
        }
        return accountRepository.findAll().stream()
                .anyMatch(a -> a.getVerifyToken() != null && a.getVerifyToken().equals(token));
    }

    public String effectiveGraphVersion(WaAccount account) {
        return StringUtils.hasText(account.getGraphVersion())
                ? account.getGraphVersion() : props.getGraph().getVersion();
    }

    private void applyRequest(WaAccount account, AccountRequest req) {
        if (StringUtils.hasText(req.getAppId())) {
            account.setAppId(req.getAppId());
        }
        if (StringUtils.hasText(req.getWabaId())) {
            account.setWabaId(req.getWabaId());
        }
        if (req.getName() != null) {
            account.setName(req.getName());
        }
        if (StringUtils.hasText(req.getAppSecret())) {
            account.setAppSecretEnc(AesUtil.encrypt(req.getAppSecret(), props.getToken().getSecret()));
        }
        if (req.getVerifyToken() != null) {
            account.setVerifyToken(req.getVerifyToken());
        }
        if (req.getCallbackUrl() != null) {
            account.setCallbackUrl(req.getCallbackUrl());
        }
        if (req.getStatus() != null) {
            account.setStatus(req.getStatus());
        }
        if (req.getGraphVersion() != null) {
            account.setGraphVersion(req.getGraphVersion());
        }
        if (req.getTimezone() != null) {
            account.setTimezone(req.getTimezone());
        }
        if (req.getCurrency() != null) {
            account.setCurrency(req.getCurrency());
        }
    }

    private AccountView toView(WaAccount a) {
        return AccountView.builder()
                .id(a.getId())
                .appId(a.getAppId())
                .wabaId(a.getWabaId())
                .name(a.getName())
                .callbackUrl(a.getCallbackUrl())
                .status(a.getStatus())
                .graphVersion(a.getGraphVersion())
                .timezone(a.getTimezone())
                .currency(a.getCurrency())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}