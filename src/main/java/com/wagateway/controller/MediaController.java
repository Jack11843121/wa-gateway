package com.wagateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.wagateway.common.ApiResponse;
import com.wagateway.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 媒体上传/下载/删除。
 */
@Tag(name = "Media", description = "媒体文件与 Graph API 媒体资源")
@RestController
@RequestMapping("/v1/accounts/{accountId}/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @Operation(summary = "上传媒体")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<JsonNode> upload(@PathVariable Long accountId,
                                        @RequestParam(required = false) String phoneNumberId,
                                        @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(mediaService.upload(accountId, phoneNumberId,
                file.getBytes(), file.getOriginalFilename(), file.getContentType()));
    }

    @Operation(summary = "获取媒体下载地址")
    @GetMapping("/{mediaId}/url")
    public ApiResponse<Map<String, String>> url(@PathVariable Long accountId, @PathVariable String mediaId) {
        return ApiResponse.ok(Map.of("url", mediaService.resolveUrl(accountId, mediaId)));
    }

    @Operation(summary = "下载媒体")
    @GetMapping("/{mediaId}")
    public ResponseEntity<byte[]> download(@PathVariable Long accountId, @PathVariable String mediaId) {
        byte[] bytes = mediaService.download(accountId, mediaId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + mediaId + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    @Operation(summary = "删除媒体")
    @DeleteMapping("/{mediaId}")
    public ApiResponse<JsonNode> delete(@PathVariable Long accountId, @PathVariable String mediaId) {
        return ApiResponse.ok(mediaService.delete(accountId, mediaId));
    }
}