package com.wagateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wagateway.client.GraphApiClient;
import com.wagateway.model.WaAccount;
import com.wagateway.model.WaTemplate;
import com.wagateway.repository.WaTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 模板管理：透传 Graph API + 本地缓存同步。
 */
@Service
public class TemplateService {

    private final AccountService accountService;
    private final GraphApiClient graphApiClient;
    private final WaTemplateRepository templateRepository;

    public TemplateService(AccountService accountService, GraphApiClient graphApiClient,
                           WaTemplateRepository templateRepository) {
        this.accountService = accountService;
        this.graphApiClient = graphApiClient;
        this.templateRepository = templateRepository;
    }

    public JsonNode listRemote(Long accountId) {
        WaAccount account = accountService.resolve(accountId);
        return graphApiClient.getTemplates(accountService.effectiveGraphVersion(account),
                account.getWabaId(), accountService.plainToken(account));
    }

    public JsonNode create(Long accountId, Map<String, Object> body) {
        WaAccount account = accountService.resolve(accountId);
        return graphApiClient.createTemplate(accountService.effectiveGraphVersion(account),
                account.getWabaId(), body, accountService.plainToken(account));
    }

    public JsonNode delete(Long accountId, String name) {
        WaAccount account = accountService.resolve(accountId);
        return graphApiClient.deleteTemplate(accountService.effectiveGraphVersion(account),
                account.getWabaId(), name, accountService.plainToken(account));
    }

    /**
     * 从 Meta 同步模板到本地缓存（幂等：先清后插）。
     */
    @Transactional
    public List<WaTemplate> sync(Long accountId) {
        WaAccount account = accountService.resolve(accountId);
        JsonNode resp = graphApiClient.getTemplates(accountService.effectiveGraphVersion(account),
                account.getWabaId(), accountService.plainToken(account));

        templateRepository.deleteByAccountId(accountId);

        JsonNode data = resp.path("data");
        if (data.isArray()) {
            for (JsonNode node : data) {
                WaTemplate t = new WaTemplate();
                t.setAccountId(accountId);
                t.setName(node.path("name").asText(null));
                t.setLanguage(node.path("language").asText(null));
                t.setCategory(node.path("category").asText(null));
                t.setStatus(node.path("status").asText(null));
                t.setTemplateId(node.path("id").asText(null));
                t.setComponents(node.path("components").isMissingNode() ? null : node.path("components").toString());
                t.setSyncedAt(LocalDateTime.now());
                templateRepository.save(t);
            }
        }
        return templateRepository.findByAccountIdOrderByIdDesc(accountId);
    }

    public List<WaTemplate> listLocal(Long accountId) {
        return templateRepository.findByAccountIdOrderByIdDesc(accountId);
    }
}