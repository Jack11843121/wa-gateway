package com.wagateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.wagateway.common.ApiResponse;
import com.wagateway.model.WaPhoneNumber;
import com.wagateway.service.PhoneNumberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 号码管理。
 */
@Tag(name = "PhoneNumbers", description = "号码注册/验证/两步验证/商业简介")
@RestController
@RequestMapping("/v1/accounts/{accountId}/phone-numbers")
public class PhoneNumberController {

    private final PhoneNumberService phoneNumberService;

    public PhoneNumberController(PhoneNumberService phoneNumberService) {
        this.phoneNumberService = phoneNumberService;
    }

    @Operation(summary = "号码列表（同步本地缓存）")
    @GetMapping
    public ApiResponse<List<WaPhoneNumber>> list(@PathVariable Long accountId) {
        return ApiResponse.ok(phoneNumberService.list(accountId));
    }

    @Operation(summary = "号码详情（远端）")
    @GetMapping("/{phoneNumberId}")
    public ApiResponse<JsonNode> get(@PathVariable Long accountId, @PathVariable String phoneNumberId) {
        return ApiResponse.ok(phoneNumberService.getRemote(accountId, phoneNumberId));
    }

    @Operation(summary = "注册号码")
    @PostMapping("/{phoneNumberId}/register")
    public ApiResponse<JsonNode> register(@PathVariable Long accountId, @PathVariable String phoneNumberId,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(phoneNumberService.register(accountId, phoneNumberId, body));
    }

    @Operation(summary = "取消注册")
    @PostMapping("/{phoneNumberId}/deregister")
    public ApiResponse<JsonNode> deregister(@PathVariable Long accountId, @PathVariable String phoneNumberId) {
        return ApiResponse.ok(phoneNumberService.deregister(accountId, phoneNumberId));
    }

    @Operation(summary = "请求验证码")
    @PostMapping("/{phoneNumberId}/request-code")
    public ApiResponse<JsonNode> requestCode(@PathVariable Long accountId, @PathVariable String phoneNumberId,
                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(phoneNumberService.requestCode(accountId, phoneNumberId, body));
    }

    @Operation(summary = "校验验证码")
    @PostMapping("/{phoneNumberId}/verify-code")
    public ApiResponse<JsonNode> verifyCode(@PathVariable Long accountId, @PathVariable String phoneNumberId,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(phoneNumberService.verifyCode(accountId, phoneNumberId, body));
    }

    @Operation(summary = "更新号码（含两步验证 pin）")
    @PostMapping("/{phoneNumberId}")
    public ApiResponse<JsonNode> update(@PathVariable Long accountId, @PathVariable String phoneNumberId,
                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(phoneNumberService.update(accountId, phoneNumberId, body));
    }

    @Operation(summary = "获取商业简介")
    @GetMapping("/{phoneNumberId}/business-profile")
    public ApiResponse<JsonNode> getBusinessProfile(@PathVariable Long accountId, @PathVariable String phoneNumberId) {
        return ApiResponse.ok(phoneNumberService.getBusinessProfile(accountId, phoneNumberId));
    }

    @Operation(summary = "更新商业简介")
    @PostMapping("/{phoneNumberId}/business-profile")
    public ApiResponse<JsonNode> updateBusinessProfile(@PathVariable Long accountId, @PathVariable String phoneNumberId,
                                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(phoneNumberService.updateBusinessProfile(accountId, phoneNumberId, body));
    }
}