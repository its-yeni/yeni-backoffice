package com.yeni.backoffice.api.payment.insight.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeni.backoffice.core.payment.config.PortfolioInsightProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions API 저수준 호출부.
 * core 모듈은 웹/HTTP 의존성이 없는 순수 도메인 모듈이라, 실제 외부 HTTP 연동은
 * api 모듈(이미 spring-boot-starter-web을 갖고 있음)에 둔다.
 * 프롬프트/스키마 조립 책임은 {@link OpenAiFailureInsightProvider}에 두고
 * 이 클래스는 순수 전송만 담당한다.
 */
@Component
public class OpenAiInsightClient {

    private final PortfolioInsightProperties properties;
    private final ObjectMapper objectMapper;
    private volatile RestClient restClient;
    private volatile int cachedTimeoutMs = -1;

    public OpenAiInsightClient(PortfolioInsightProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String complete(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        PortfolioInsightProperties.Openai config = properties.getOpenai();
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY가 설정되지 않았습니다.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", jsonSchema
        ));

        String rawResponse = client(config.getTimeoutMs())
                .post()
                .uri(config.getBaseUrl() + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return extractMessageContent(rawResponse);
    }

    private String extractMessageContent(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalStateException("OpenAI 응답에 message.content가 없습니다: " + rawResponse);
            }
            return content.asText();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("OpenAI 응답 파싱에 실패했습니다.", e);
        }
    }

    private synchronized RestClient client(int timeoutMs) {
        if (restClient == null || cachedTimeoutMs != timeoutMs) {
            ClientHttpRequestFactory requestFactory = buildRequestFactory(timeoutMs);
            restClient = RestClient.builder().requestFactory(requestFactory).build();
            cachedTimeoutMs = timeoutMs;
        }
        return restClient;
    }

    private ClientHttpRequestFactory buildRequestFactory(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return factory;
    }
}
