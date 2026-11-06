package com.wagateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 健康检查。
 */
@Tag(name = "Health", description = "存活/就绪探针")
@RestController
@RequestMapping("/v1/health")
public class HealthController {

    @Operation(summary = "存活探测")
    @GetMapping("/live")
    public Map<String, Object> live() {
        return Map.of("status", "UP", "time", Instant.now().toString());
    }

    @Operation(summary = "简单健康")
    @GetMapping
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "wa-gateway");
    }
}