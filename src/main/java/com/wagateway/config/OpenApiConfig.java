package com.wagateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI waGatewayOpenAPI() {
        String apiKeyScheme = "ApiKey";
        return new OpenAPI()
                .info(new Info()
                        .title("wa-gateway API")
                        .description("开源的 WhatsApp Cloud API 网关：消息/媒体/模板/号码/Webhook 一站式接入。"
                                + "\n除注明需要鉴权的管理接口外，均需在请求头携带 X-Api-Key。")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(apiKeyScheme))
                .components(new Components().addSecuritySchemes(apiKeyScheme,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Api-Key")));
    }
}