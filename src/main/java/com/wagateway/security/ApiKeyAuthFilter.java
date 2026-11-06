package com.wagateway.security;

import com.wagateway.common.Constants;
import com.wagateway.common.ErrorCode;
import com.wagateway.config.GatewayProperties;
import com.wagateway.util.JsonUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * REST API 访问鉴权：校验 X-Api-Key。
 * webhook / health 路径不鉴权（分别由 Meta 与探针访问）。
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final GatewayProperties props;

    public ApiKeyAuthFilter(GatewayProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (!path.startsWith("/v1/")) {
            return true;
        }
        return path.startsWith("/v1/webhooks") || path.startsWith("/v1/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(Constants.HEADER_API_KEY);
        boolean valid = StringUtils.hasText(apiKey)
                && props.getAuth().getApiKeys().stream().anyMatch(k -> k.equals(apiKey));
        if (!valid) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(JsonUtil.toJson(Map.of(
                    "code", ErrorCode.UNAUTHORIZED.getCode(),
                    "message", ErrorCode.UNAUTHORIZED.getMessage())));
            return;
        }
        chain.doFilter(request, response);
    }
}