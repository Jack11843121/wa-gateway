package com.wagateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.wagateway.common.ApiResponse;
import com.wagateway.model.WaTemplate;
import com.wagateway.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 模板管理。
 */
@Tag(name = "Templates", description = "消息模板管理（透传 Graph API + 本地缓存）")
@RestController
@RequestMapping("/v1/accounts/{accountId}/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @Operation(summary = "获取远端模板列表")
    @GetMapping
    public ApiResponse<JsonNode> listRemote(@PathVariable Long accountId) {
        return ApiResponse.ok(templateService.listRemote(accountId));
    }

    @Operation(summary = "获取本地缓存模板列表")
    @GetMapping("/cached")
    public ApiResponse<List<WaTemplate>> listLocal(@PathVariable Long accountId) {
        return ApiResponse.ok(templateService.listLocal(accountId));
    }

    @Operation(summary = "创建模板")
    @PostMapping
    public ApiResponse<JsonNode> create(@PathVariable Long accountId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(templateService.create(accountId, body));
    }

    @Operation(summary = "同步模板到本地缓存")
    @PostMapping("/sync")
    public ApiResponse<List<WaTemplate>> sync(@PathVariable Long accountId) {
        return ApiResponse.ok(templateService.sync(accountId));
    }

    @Operation(summary = "删除模板（按名称）")
    @DeleteMapping("/{name}")
    public ApiResponse<JsonNode> delete(@PathVariable Long accountId, @PathVariable String name) {
        return ApiResponse.ok(templateService.delete(accountId, name));
    }
}