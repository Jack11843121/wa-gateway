package com.wagateway.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.wagateway.common.Constants;
import com.wagateway.config.GatewayProperties;
import com.wagateway.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Meta Graph API 客户端（薄封装）。
 *
 * <p>所有方法以 JsonNode 透传 Meta 响应，出错时抛出 {@link GraphApiException}。
 * 采用「纯网关」理念：消息/模板等复杂结构直接透传，不在此处重定义 Meta 报文。</p>
 */
@Slf4j
@Component
public class GraphApiClient {

    private final RestTemplate graphRestTemplate;
    private final GatewayProperties props;

    public GraphApiClient(RestTemplate graphRestTemplate, GatewayProperties props) {
        this.graphRestTemplate = graphRestTemplate;
        this.props = props;
    }

    // ---------------------------------------------------------------- Messages

    public JsonNode sendMessage(String version, String phoneNumberId, Map<String, Object> body, String token) {
        String url = base(version) + "/" + phoneNumberId + "/messages";
        return toJsonNode(exchange(HttpMethod.POST, url, JsonUtil.toJson(body), token, MediaType.APPLICATION_JSON));
    }

    public JsonNode markMessageRead(String version, String phoneNumberId, Map<String, Object> body, String token) {
        String url = base(version) + "/" + phoneNumberId + "/messages";
        return toJsonNode(exchange(HttpMethod.PUT, url, JsonUtil.toJson(body), token, MediaType.APPLICATION_JSON));
    }

    // ---------------------------------------------------------------- Media

    public JsonNode uploadMedia(String version, String phoneNumberId, byte[] file, String filename, String mimeType, String token) {
        String url = base(version) + "/" + phoneNumberId + "/media";

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(
                mimeType != null && !mimeType.isBlank() ? mimeType : MediaType.APPLICATION_OCTET_STREAM_VALUE));
        ByteArrayResource resource = new ByteArrayResource(file) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add("file", new HttpEntity<>(resource, fileHeaders));
        multipart.add("messaging_product", Constants.MESSAGING_PRODUCT);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(multipart, headers);

        return toJsonNode(exchangeEntity(HttpMethod.POST, url, entity));
    }

    public String retrieveMediaUrl(String version, String mediaId, String token) {
        String url = base(version) + "/" + mediaId;
        JsonNode node = toJsonNode(exchange(HttpMethod.GET, url, null, token, null));
        JsonNode urlNode = node.path("url");
        if (urlNode.isMissingNode() || urlNode.asText().isBlank()) {
            throw new GraphApiException(0, "media", "media url not found in response", null);
        }
        return urlNode.asText();
    }

    public byte[] downloadMedia(String absoluteUrl, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<byte[]> resp = graphRestTemplate.exchange(absoluteUrl, HttpMethod.GET, entity, byte[].class);
        return resp.getBody();
    }

    public JsonNode deleteMedia(String version, String mediaId, String token) {
        String url = base(version) + "/" + mediaId;
        return toJsonNode(exchange(HttpMethod.DELETE, url, null, token, null));
    }

    // ---------------------------------------------------------------- Phone Numbers

    public JsonNode getPhoneNumbers(String version, String wabaId, String token) {
        String url = UriComponentsBuilder
                .fromHttpUrl(base(version) + "/" + wabaId + "/phone_numbers")
                .queryParam("fields", "id,display_phone_number,verified_name,quality_rating,status,messaging_limit_tier")
                .build().toUriString();
        return toJsonNode(exchange(HttpMethod.GET, url, null, token, null));
    }

    public JsonNode getPhoneNumberById(String version, String phoneNumberId, String token) {
        String url = base(version) + "/" + phoneNumberId;
        return toJsonNode(exchange(HttpMethod.GET, url, null, token, null));
    }

    /**
     * 通用号码更新（含两步验证 pin 设置）：POST /{phoneNumberId}
     */
    public JsonNode updatePhoneNumber(String version, String phoneNumberId, Map<String, Object> body, String token) {
        return toJsonNode(exchange(HttpMethod.POST, base(version) + "/" + phoneNumberId,
                JsonUtil.toJson(body), token, MediaType.APPLICATION_JSON));
    }

    public JsonNode registerPhone(String version, String phoneNumberId, Map<String, Object> body, String token) {
        return toJsonNode(exchange(HttpMethod.POST, base(version) + "/" + phoneNumberId + "/register",
                JsonUtil.toJson(body), token, MediaType.APPLICATION_JSON));
    }

    public JsonNode deregisterPhone(String version, String phoneNumberId, String token) {
        return toJsonNode(exchange(HttpMethod.POST, base(version) + "/" + phoneNumberId + "/deregister",
                "{}", token, MediaType.APPLICATION_JSON));
    }

    public JsonNode requestVerificationCode(String version, String phoneNumberId, Map<String, Object> body, String token) {
        return toJsonNode(exchange(HttpMethod.POST, base(version) + "/" + phoneNumberId + "/request_code",
                JsonUtil.toJson(body), token, MediaType.APPLICATION_JSON));
    }

    public JsonNode verifyCode(String version, String phoneNumberId, Map<String, Object> body, String token) {
        return toJsonNode(exchange(HttpMethod.POST, base(version) + "/" + phoneNumberId + "/verify_code",
                JsonUtil.toJson(body), token, MediaType.APPLICATION_JSON));
    }

    // ---------------------------------------------------------------- Business Profile

    public JsonNode getBusinessProfile(String version, String phoneNumberId, String token) {
        return toJsonNode(exchange(HttpMethod.GET,
                base(version) + "/" + phoneNumberId + "/whatsapp_business_profile", null, token, null));
    }

    public JsonNode updateBusinessProfile(String version, String phoneNumberId, Map<String, Object> body, String token) {
        return toJsonNode(exchange(HttpMethod.POST,
                base(version) + "/" + phoneNumberId + "/whatsapp_business_profile",
                JsonUtil.toJson(body), token, MediaType.APPLICATION_JSON));
    }

    // ---------------------------------------------------------------- Templates

    public JsonNode createTemplate(String version, String wabaId, Map<String, Object> body, String token) {
        return toJsonNode(exchange(HttpMethod.POST, base(version) + "/" + wabaId + "/message_templates",
                JsonUtil.toJson(body), token, MediaType.APPLICATION_JSON));
    }

    public JsonNode getTemplates(String version, String wabaId, String token) {
        String url = UriComponentsBuilder
                .fromHttpUrl(base(version) + "/" + wabaId + "/message_templates")
                .queryParam("limit", 100)
                .build().toUriString();
        return toJsonNode(exchange(HttpMethod.GET, url, null, token, null));
    }

    public JsonNode deleteTemplate(String version, String wabaId, String templateName, String token) {
        String url = UriComponentsBuilder
                .fromHttpUrl(base(version) + "/" + wabaId + "/message_templates")
                .queryParam("name", templateName)
                .build().toUriString();
        return toJsonNode(exchange(HttpMethod.DELETE, url, null, token, null));
    }

    // ---------------------------------------------------------------- internals

    private String base(String version) {
        String v = (version != null && !version.isBlank()) ? version : props.getGraph().getVersion();
        return props.getGraph().getBaseUrl() + "/" + v;
    }

    private JsonNode toJsonNode(String body) {
        return body == null ? JsonUtil.mapper().createObjectNode() : JsonUtil.toJsonNode(body);
    }

    private String exchange(HttpMethod method, String url, String jsonBody, String token, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        if (contentType != null) {
            headers.setContentType(contentType);
        }
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        return exchangeEntity(method, url, entity);
    }

    private String exchangeEntity(HttpMethod method, String url, HttpEntity<?> entity) {
        try {
            ResponseEntity<String> resp = graphRestTemplate.exchange(url, method, entity, String.class);
            checkError(resp.getBody());
            return resp.getBody();
        } catch (HttpStatusCodeException e) {
            throw toGraphException(e.getResponseBodyAsString());
        }
    }

    private void checkError(String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        JsonNode node = JsonUtil.toJsonNode(body);
        JsonNode error = node.get("error");
        if (error != null) {
            throw new GraphApiException(
                    error.path("code").asInt(0),
                    error.path("type").asText(null),
                    error.path("message").asText("unknown error"),
                    error.path("fbtrace_id").asText(null));
        }
    }

    private GraphApiException toGraphException(String body) {
        try {
            JsonNode node = JsonUtil.toJsonNode(body == null ? "{}" : body);
            JsonNode error = node.get("error");
            if (error != null) {
                return new GraphApiException(
                        error.path("code").asInt(0),
                        error.path("type").asText(null),
                        error.path("message").asText("unknown error"),
                        error.path("fbtrace_id").asText(null));
            }
        } catch (Exception ignore) {
            // fallthrough
        }
        return new GraphApiException(0, "http", body == null ? "null" : body, null);
    }
}