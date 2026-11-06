package com.wagateway.controller;

import com.wagateway.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meta Webhook 入口（免鉴权）。
 */
@Slf4j
@Tag(name = "Webhooks", description = "Meta 回调验证与事件接收")
@RestController
@RequestMapping("/v1/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @Operation(summary = "webhook 验证（Meta 握手）")
    @GetMapping
    public ResponseEntity<String> verify(@RequestParam("hub.mode") String mode,
                                         @RequestParam("hub.verify_token") String verifyToken,
                                         @RequestParam("hub.challenge") String challenge) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(webhookService.verify(mode, verifyToken, challenge));
    }

    @Operation(summary = "webhook 事件接收")
    @PostMapping
    public ResponseEntity<Void> event(HttpServletRequest request) throws Exception {
        byte[] body = request.getInputStream().readAllBytes();
        String signature = request.getHeader("X-Hub-Signature-256");
        webhookService.handleEvent(body, signature);
        // Meta 要求快速 200，事件处理在 MQ 中异步完成
        return ResponseEntity.ok().build();
    }
}