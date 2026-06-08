package com.klingai.poc.hello.klingmcp.video.api;

import com.klingai.poc.hello.klingmcp.auth.OwnerIdentity;
import com.klingai.poc.hello.klingmcp.config.KlingMcpProperties;
import com.klingai.poc.hello.klingmcp.video.model.VideoContracts;
import com.klingai.poc.hello.klingmcp.video.model.VideoTaskStatus;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpKlingApiClient implements KlingApiClient {

    private final KlingMcpProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public HttpKlingApiClient(KlingMcpProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl(properties.api().resolvedBaseUrl())
                .build();
    }

    @Override
    public KlingCreateVideoResult createVideo(
            String localTaskId,
            VideoContracts.CreateVideoRequest request,
            OwnerIdentity owner,
            String callbackUrl) {
        Map<String, Object> payload = createVideoPayload(localTaskId, request, owner, callbackUrl);
        try {
            JsonNode body = restClient.post()
                    .uri(properties.api().resolvedCreateVideoPath())
                    .headers(this::applyAuth)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                        throw new KlingApiException(
                                "KLING_HTTP_" + response.getStatusCode().value(),
                                "Kling API rejected the video generation request",
                                response.getStatusCode().is5xxServerError());
                    })
                    .body(JsonNode.class);
            return parseCreateVideoResponse(body);
        }
        catch (RestClientResponseException ex) {
            throw new KlingApiException(
                    "KLING_HTTP_" + ex.getStatusCode().value(),
                    "Kling API rejected the video generation request",
                    ex.getStatusCode().is5xxServerError());
        }
    }

    @Override
    public boolean cancelVideo(String providerTaskId) {
        if (!StringUtils.hasText(providerTaskId)) {
            return false;
        }
        try {
            restClient.post()
                    .uri(properties.api().resolvedCancelVideoPath(), providerTaskId)
                    .headers(this::applyAuth)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        }
        catch (RestClientResponseException ex) {
            throw new KlingApiException(
                    "KLING_HTTP_" + ex.getStatusCode().value(),
                    "Kling API rejected the cancellation request",
                    ex.getStatusCode().is5xxServerError());
        }
    }

    private Map<String, Object> createVideoPayload(
            String localTaskId,
            VideoContracts.CreateVideoRequest request,
            OwnerIdentity owner,
            String callbackUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "prompt", request.prompt());
        putIfPresent(payload, "model", request.model());
        putIfPresent(payload, "durationSeconds", request.durationSeconds());
        putIfPresent(payload, "aspectRatio", request.aspectRatio());
        putIfPresent(payload, "negativePrompt", request.negativePrompt());
        putIfPresent(payload, "seed", request.seed());
        putIfPresent(payload, "imageUrl", request.imageUrl());
        putIfPresent(payload, "callbackUrl", callbackUrl);
        putIfPresent(payload, "idempotencyKey", request.idempotencyKey());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("localTaskId", localTaskId);
        metadata.put("ownerSubject", owner.subject());
        metadata.put("clientId", owner.clientId());
        metadata.put("organizationId", owner.organizationId());
        payload.put("metadata", metadata);
        return payload;
    }

    private KlingCreateVideoResult parseCreateVideoResponse(JsonNode body) {
        if (body == null || body.isNull()) {
            throw new KlingApiException("KLING_EMPTY_RESPONSE", "Kling API returned an empty response", true);
        }
        JsonNode data = body.path("data").isMissingNode() ? body : body.path("data");
        String providerTaskId = firstText(data, "taskId", "task_id", "id", "providerTaskId", "provider_task_id");
        if (!StringUtils.hasText(providerTaskId)) {
            throw new KlingApiException("KLING_MISSING_TASK_ID", "Kling API response did not include a task id", false);
        }
        VideoTaskStatus status = VideoTaskStatus.fromProviderStatus(firstText(data, "status", "state"));
        Integer progress = firstInteger(data, "progress", "percent");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = objectMapper.convertValue(body, Map.class);
        return new KlingCreateVideoResult(providerTaskId, status, progress, metadata);
    }

    private void applyAuth(HttpHeaders headers) {
        String apiKey = properties.api().apiKey();
        if (!StringUtils.hasText(apiKey)) {
            return;
        }
        String authHeader = properties.api().resolvedAuthHeader();
        if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(authHeader)
                && !apiKey.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())
                && !apiKey.regionMatches(true, 0, "Basic ", 0, "Basic ".length())) {
            headers.setBearerAuth(apiKey);
            return;
        }
        headers.set(authHeader, apiKey);
    }

    private static void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value instanceof String stringValue && !StringUtils.hasText(stringValue)) {
            return;
        }
        if (value != null) {
            payload.put(key, value);
        }
    }

    private static String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return null;
    }

    private static Integer firstInteger(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isInt()) {
                return value.asInt();
            }
            if (value.isTextual()) {
                try {
                    return Integer.parseInt(value.asText());
                }
                catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
