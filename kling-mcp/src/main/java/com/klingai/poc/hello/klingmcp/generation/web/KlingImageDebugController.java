package com.klingai.poc.hello.klingmcp.generation.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.klingai.poc.hello.klingmcp.auth.OwnerIdentity;
import com.klingai.poc.hello.klingmcp.config.KlingMcpProperties;
import com.klingai.poc.hello.klingmcp.generation.api.FeignKlingApiClient;
import com.klingai.poc.hello.klingmcp.generation.api.KlingApiException;
import com.klingai.poc.hello.klingmcp.generation.api.KlingCreateImageResult;
import com.klingai.poc.hello.klingmcp.generation.model.ImageContracts;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(path = "/api/kling/debug/images", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kling.mcp.debug-api", name = "enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Kling Local Debug", description = "Local endpoints for verifying Kling upstream APIs.")
public class KlingImageDebugController {

    private static final OwnerIdentity LOCAL_OWNER = new OwnerIdentity(
            "local-debug",
            "local-debug-client",
            "local",
            List.of("SCOPE_kling:invoke"));

    private final FeignKlingApiClient klingApiClient;
    private final KlingMcpProperties properties;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a Kling image task through FeignKlingApiClient")
    ResponseEntity<KlingLocalImageResponse> createImage(@RequestBody KlingLocalImageRequest request) {
        if (request == null || !StringUtils.hasText(request.prompt())) {
            return ResponseEntity.badRequest()
                    .body(KlingLocalImageResponse.error(null, "INVALID_PROMPT", "prompt is required", false));
        }

        String localTaskId = UUID.randomUUID().toString();
        try {
            KlingCreateImageResult result = klingApiClient.createImage(
                    localTaskId,
                    request.toCreateImageRequest(),
                    LOCAL_OWNER,
                    properties.imageCallbackUri());
            return ResponseEntity.ok(KlingLocalImageResponse.ok(localTaskId, result));
        }
        catch (KlingApiException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(KlingLocalImageResponse.error(localTaskId, ex.getCode(), ex.getMessage(), ex.isRetryable()));
        }
    }

    public record KlingLocalImageRequest(
            String prompt,
            String model,
            String aspectRatio,
            String negativePrompt,
            Long seed,
            Integer count,
            String style,
            String referenceImageUrl,
            String idempotencyKey) {

        private ImageContracts.CreateImageRequest toCreateImageRequest() {
            return new ImageContracts.CreateImageRequest(
                    prompt,
                    model,
                    aspectRatio,
                    negativePrompt,
                    seed,
                    count,
                    style,
                    referenceImageUrl,
                    idempotencyKey);
        }
    }

    public record KlingLocalImageResponse(
            boolean ok,
            String localTaskId,
            String providerTaskId,
            String status,
            Integer progress,
            Map<String, Object> metadata,
            ErrorDetail error) {

        static KlingLocalImageResponse ok(String localTaskId, KlingCreateImageResult result) {
            return new KlingLocalImageResponse(
                    true,
                    localTaskId,
                    result.providerTaskId(),
                    result.status() == null ? null : result.status().name(),
                    result.progress(),
                    result.metadata(),
                    null);
        }

        static KlingLocalImageResponse error(String localTaskId, String code, String message, boolean retryable) {
            return new KlingLocalImageResponse(
                    false,
                    localTaskId,
                    null,
                    null,
                    null,
                    null,
                    new ErrorDetail(code, message, retryable));
        }
    }

    public record ErrorDetail(String code, String message, boolean retryable) {
    }
}
