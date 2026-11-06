package com.wagateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wagateway.client.GraphApiClient;
import com.wagateway.model.WaAccount;
import org.springframework.stereotype.Service;

/**
 * 媒体上传/下载/删除（透传 Graph API）。
 */
@Service
public class MediaService {

    private final AccountService accountService;
    private final GraphApiClient graphApiClient;

    public MediaService(AccountService accountService, GraphApiClient graphApiClient) {
        this.accountService = accountService;
        this.graphApiClient = graphApiClient;
    }

    public JsonNode upload(Long accountId, String phoneNumberId, byte[] file, String filename, String mimeType) {
        WaAccount account = accountService.resolve(accountId);
        return graphApiClient.uploadMedia(accountService.effectiveGraphVersion(account), phoneNumberId,
                file, filename, mimeType, accountService.plainToken(account));
    }

    public String resolveUrl(Long accountId, String mediaId) {
        WaAccount account = accountService.resolve(accountId);
        return graphApiClient.retrieveMediaUrl(accountService.effectiveGraphVersion(account),
                mediaId, accountService.plainToken(account));
    }

    public byte[] download(Long accountId, String mediaId) {
        String url = resolveUrl(accountId, mediaId);
        WaAccount account = accountService.resolve(accountId);
        return graphApiClient.downloadMedia(url, accountService.plainToken(account));
    }

    public JsonNode delete(Long accountId, String mediaId) {
        WaAccount account = accountService.resolve(accountId);
        return graphApiClient.deleteMedia(accountService.effectiveGraphVersion(account),
                mediaId, accountService.plainToken(account));
    }
}