package com.wagateway.controller;

import com.wagateway.common.ApiResponse;
import com.wagateway.dto.AccountRequest;
import com.wagateway.dto.AccountView;
import com.wagateway.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 账号（WABA）管理。
 */
@Tag(name = "Accounts", description = "WhatsApp Business Account 账号接入管理")
@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "接入账号")
    @PostMapping
    public ApiResponse<AccountView> create(@RequestBody AccountRequest req) {
        return ApiResponse.ok(accountService.create(req));
    }

    @Operation(summary = "账号列表")
    @GetMapping
    public ApiResponse<List<AccountView>> list() {
        return ApiResponse.ok(accountService.list());
    }

    @Operation(summary = "账号详情")
    @GetMapping("/{accountId}")
    public ApiResponse<AccountView> get(@PathVariable Long accountId) {
        return ApiResponse.ok(accountService.get(accountId));
    }

    @Operation(summary = "更新账号")
    @PutMapping("/{accountId}")
    public ApiResponse<AccountView> update(@PathVariable Long accountId, @RequestBody AccountRequest req) {
        return ApiResponse.ok(accountService.update(accountId, req));
    }

    @Operation(summary = "停用账号")
    @DeleteMapping("/{accountId}")
    public ApiResponse<Void> delete(@PathVariable Long accountId) {
        accountService.delete(accountId);
        return ApiResponse.ok();
    }
}