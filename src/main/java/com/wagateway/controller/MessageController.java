package com.wagateway.controller;

import com.wagateway.common.ApiResponse;
import com.wagateway.dto.SendResponse;
import com.wagateway.model.WaMessage;
import com.wagateway.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 消息发送与查询。
 */
@Tag(name = "Messages", description = "消息发送（异步入队）与状态查询")
@RestController
@RequestMapping("/v1")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @Operation(summary = "发送消息", description = "body 为 Meta 原生消息体 {messaging_product,to,type,...}；"
            + "默认异步入队，可用 query async=false 同步直连。")
    @PostMapping("/accounts/{accountId}/messages")
    public ApiResponse<SendResponse> send(@PathVariable Long accountId,
                                          @RequestParam(required = false) String phoneNumberId,
                                          @RequestParam(defaultValue = "true") boolean async,
                                          @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(messageService.send(accountId, phoneNumberId, body, requestId, async));
    }

    @Operation(summary = "查询消息状态（按 wamid）")
    @GetMapping("/messages/{messageId}")
    public ApiResponse<WaMessage> getByMessageId(@PathVariable String messageId) {
        return ApiResponse.ok(messageService.getByMessageId(messageId));
    }

    @Operation(summary = "查询消息状态（按调用方幂等键）")
    @GetMapping("/messages/request/{requestId}")
    public ApiResponse<WaMessage> getByRequestId(@PathVariable String requestId) {
        return ApiResponse.ok(messageService.getByRequestId(requestId));
    }

    @Operation(summary = "标记已读", description = "body: {messaging_product:'whatsapp',status:'read',message_id:'wamid'}")
    @PutMapping("/accounts/{accountId}/messages/read")
    public ApiResponse<Object> markRead(@PathVariable Long accountId,
                                        @RequestParam(required = false) String phoneNumberId,
                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(messageService.markRead(accountId, phoneNumberId, body));
    }
}