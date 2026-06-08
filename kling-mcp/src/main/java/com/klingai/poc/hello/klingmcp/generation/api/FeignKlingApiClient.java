package com.klingai.poc.hello.klingmcp.generation.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klingai.poc.hello.klingfeignclient.KlingAccessTokenProvider;
import com.klingai.poc.hello.klingfeignclient.KlingFeignClient;
import com.klingai.poc.hello.klingmcp.auth.OwnerIdentity;
import com.klingai.poc.hello.klingmcp.generation.model.GenerationTaskStatus;
import com.klingai.poc.hello.klingmcp.generation.model.ImageContracts;
import com.klingai.poc.hello.klingmcp.generation.model.VideoContracts;

import feign.FeignException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FeignKlingApiClient implements KlingApiClient {

    private final ObjectMapper objectMapper;
    private final KlingFeignClient feignClient;
    private final KlingAccessTokenProvider accessTokenProvider;

    @Override
    public KlingCreateVideoResult createVideo(
            String localTaskId,
            VideoContracts.CreateVideoRequest request,
            OwnerIdentity owner,
            String callbackUrl) {
        Map<String, Object> payload = createVideoPayload(localTaskId, request, owner, callbackUrl);
        try {
            JsonNode body = feignClient.createVideo(authHeaders(), payload);
            return parseCreateVideoResponse(body);
        }
        catch (FeignException ex) {
            throw new KlingApiException(
                    "KLING_HTTP_" + ex.status(),
                    "Kling API rejected the video generation request",
                    isRetryable(ex));
        }
    }

    @Override
    public boolean cancelVideo(String providerTaskId) {
        if (!StringUtils.hasText(providerTaskId)) {
            return false;
        }
        try {
            feignClient.cancelVideo(authHeaders(), providerTaskId);
            return true;
        }
        catch (FeignException ex) {
            throw new KlingApiException(
                    "KLING_HTTP_" + ex.status(),
                    "Kling API rejected the cancellation request",
                    isRetryable(ex));
        }
    }

    @Override
    public KlingCreateImageResult createImage(
            String localTaskId,
            ImageContracts.CreateImageRequest request,
            OwnerIdentity owner,
            String callbackUrl) {
        Map<String, Object> payload = createImagePayload(localTaskId, request, owner, callbackUrl);
        try {
            JsonNode body = feignClient.createImage(authHeaders(), payload);
            return parseCreateImageResponse(body);
        }
        catch (FeignException ex) {
            throw new KlingApiException(
                    "KLING_HTTP_" + ex.status(),
                    "Kling API rejected the image generation request",
                    isRetryable(ex));
        }
    }

    @Override
    public boolean cancelImage(String providerTaskId) {
        if (!StringUtils.hasText(providerTaskId)) {
            return false;
        }
        try {
            feignClient.cancelImage(authHeaders(), providerTaskId);
            return true;
        }
        catch (FeignException ex) {
            throw new KlingApiException(
                    "KLING_HTTP_" + ex.status(),
                    "Kling API rejected the image cancellation request",
                    isRetryable(ex));
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

    private Map<String, Object> createImagePayload(
            String localTaskId,
            ImageContracts.CreateImageRequest request,
            OwnerIdentity owner,
            String callbackUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "prompt", request.prompt());
        putIfPresent(payload, "model", request.model());
        putIfPresent(payload, "aspectRatio", request.aspectRatio());
        putIfPresent(payload, "negativePrompt", request.negativePrompt());
        putIfPresent(payload, "seed", request.seed());
        putIfPresent(payload, "count", request.count());
        putIfPresent(payload, "style", request.style());
        putIfPresent(payload, "referenceImageUrl", request.referenceImageUrl());
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
        GenerationTaskStatus status = GenerationTaskStatus.fromProviderStatus(firstText(data, "status", "state"));
        Integer progress = firstInteger(data, "progress", "percent");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = objectMapper.convertValue(body, Map.class);
        return new KlingCreateVideoResult(providerTaskId, status, progress, metadata);
    }

    private KlingCreateImageResult parseCreateImageResponse(JsonNode body) {
        if (body == null || body.isNull()) {
            throw new KlingApiException("KLING_EMPTY_RESPONSE", "Kling API returned an empty response", true);
        }
        JsonNode data = body.path("data").isMissingNode() ? body : body.path("data");
        String providerTaskId = firstText(data, "taskId", "task_id", "id", "providerTaskId", "provider_task_id");
        if (!StringUtils.hasText(providerTaskId)) {
            throw new KlingApiException("KLING_MISSING_TASK_ID", "Kling API response did not include a task id", false);
        }
        GenerationTaskStatus status = GenerationTaskStatus.fromProviderStatus(firstText(data, "status", "state"));
        Integer progress = firstInteger(data, "progress", "percent");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = objectMapper.convertValue(body, Map.class);
        return new KlingCreateImageResult(providerTaskId, status, progress, metadata);
    }

    private Map<String, String> authHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenProvider.currentToken());
        return headers;
    }

    private static boolean isRetryable(FeignException ex) {
        return ex.status() < 0 || ex.status() >= 500;
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
