package com.klingai.poc.hello.klingmcp.generation.service;

import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klingai.poc.hello.klingmcp.auth.OwnerIdentity;
import com.klingai.poc.hello.klingmcp.config.KlingMcpProperties;
import com.klingai.poc.hello.klingmcp.generation.api.KlingApiClient;
import com.klingai.poc.hello.klingmcp.generation.api.KlingApiException;
import com.klingai.poc.hello.klingmcp.generation.api.KlingCreateImageResult;
import com.klingai.poc.hello.klingmcp.generation.model.ImageContracts;
import com.klingai.poc.hello.klingmcp.generation.model.ImageTask;
import com.klingai.poc.hello.klingmcp.generation.model.GenerationTaskStatus;
import com.klingai.poc.hello.klingmcp.generation.repository.ImageTaskRepository;

@Service
@RequiredArgsConstructor
public class KlingImageService {

    private static final int DEFAULT_WAIT_SECONDS = 30;
    private static final int MAX_WAIT_SECONDS = 60;
    private static final int DEFAULT_LIST_LIMIT = 20;

    private final ImageTaskRepository repository;
    private final KlingApiClient klingApiClient;
    private final KlingMcpProperties properties;
    private final ObjectMapper objectMapper;

    public ImageContracts.ImageTaskResponse createImage(ImageContracts.CreateImageRequest request) {
        if (request == null || !StringUtils.hasText(request.prompt())) {
            return ImageContracts.ImageTaskResponse.error(
                    "INVALID_PROMPT",
                    "prompt is required",
                    false,
                    "Provide a non-empty prompt and call create_image again.");
        }

        OwnerIdentity owner = currentOwner();
        if (owner == null) {
            return unauthenticatedResponse();
        }

        Instant now = Instant.now();
        String taskId = UUID.randomUUID().toString();
        ImageTask task = repository.save(ImageTask.create(taskId, owner, request, now));

        try {
            KlingCreateImageResult providerResult = klingApiClient.createImage(
                    taskId,
                    request,
                    owner,
                    properties.imageCallbackUri());
            task = task.withProviderSubmission(
                    providerResult.providerTaskId(),
                    providerResult.status(),
                    providerResult.progress(),
                    Instant.now());
            repository.save(task);
            return ImageContracts.ImageTaskResponse.fromTask(task, nextActionFor(task));
        }
        catch (KlingApiException ex) {
            task = task.withFailure(new ImageContracts.ImageTaskError(ex.getCode(), ex.getMessage(), ex.isRetryable()), Instant.now());
            repository.save(task);
            return ImageContracts.ImageTaskResponse.fromTask(task, "Fix the request or retry after the upstream API recovers.");
        }
    }

    public ImageContracts.ImageTaskResponse getImageTask(ImageContracts.GetImageTaskRequest request) {
        OwnerIdentity owner = currentOwner();
        if (owner == null) {
            return unauthenticatedResponse();
        }
        return visibleTask(request == null ? null : request.taskId(), owner)
                .map(task -> ImageContracts.ImageTaskResponse.fromTask(task, nextActionFor(task)))
                .orElseGet(() -> taskNotFoundResponse());
    }

    public ImageContracts.ListImageTasksResponse listImageTasks(ImageContracts.ListImageTasksRequest request) {
        OwnerIdentity owner = currentOwner();
        if (owner == null) {
            return ImageContracts.ListImageTasksResponse.error("UNAUTHENTICATED", "Authentication is required", false);
        }
        int limit = request == null || request.limit() == null ? DEFAULT_LIST_LIMIT : request.limit();
        GenerationTaskStatus status = request == null ? null : request.status();
        String cursor = request == null ? null : request.cursor();
        List<ImageTask> tasks = repository.findByOwner(owner.subject(), status, limit, cursor);
        List<ImageContracts.ImageTaskSummary> summaries = tasks.stream()
                .map(ImageContracts.ImageTaskSummary::fromTask)
                .toList();
        String nextCursor = tasks.size() == Math.max(1, Math.min(100, limit))
                ? tasks.get(tasks.size() - 1).updatedAt().toString()
                : null;
        return ImageContracts.ListImageTasksResponse.ok(summaries, nextCursor);
    }

    public ImageContracts.ImageTaskResponse waitForImageTask(ImageContracts.WaitImageTaskRequest request) {
        OwnerIdentity owner = currentOwner();
        if (owner == null) {
            return unauthenticatedResponse();
        }
        if (request == null || !StringUtils.hasText(request.taskId())) {
            return taskNotFoundResponse();
        }
        int timeoutSeconds = request.timeoutSeconds() == null ? DEFAULT_WAIT_SECONDS : request.timeoutSeconds();
        timeoutSeconds = Math.max(0, Math.min(MAX_WAIT_SECONDS, timeoutSeconds));
        Instant deadline = Instant.now().plusSeconds(timeoutSeconds);

        ImageTask task = visibleTask(request.taskId(), owner).orElse(null);
        if (task == null) {
            return taskNotFoundResponse();
        }
        while (!task.status().isTerminal() && Instant.now().isBefore(deadline)) {
            sleepOneSecond();
            task = visibleTask(request.taskId(), owner).orElse(null);
            if (task == null) {
                return taskNotFoundResponse();
            }
        }

        return ImageContracts.ImageTaskResponse.fromTask(
                task,
                task.status().isTerminal()
                        ? nextActionFor(task)
                        : "Task is still running. Call get_image_task or wait_for_image_task again later.");
    }

    public ImageContracts.ImageTaskResponse cancelImageTask(ImageContracts.CancelImageTaskRequest request) {
        OwnerIdentity owner = currentOwner();
        if (owner == null) {
            return unauthenticatedResponse();
        }
        ImageTask task = visibleTask(request == null ? null : request.taskId(), owner).orElse(null);
        if (task == null) {
            return taskNotFoundResponse();
        }
        if (task.status().isTerminal()) {
            return ImageContracts.ImageTaskResponse.fromTask(task, "Task is already terminal and cannot be cancelled.");
        }
        try {
            klingApiClient.cancelImage(task.providerTaskId());
            task = task.withCancelled(Instant.now());
            repository.save(task);
            return ImageContracts.ImageTaskResponse.fromTask(task, "Task cancellation has been requested.");
        }
        catch (KlingApiException ex) {
            return new ImageContracts.ImageTaskResponse(
                    false,
                    task.taskId(),
                    task.providerTaskId(),
                    task.status(),
                    task.createdAt(),
                    task.updatedAt(),
                    task.progress(),
                    task.result(),
                    new ImageContracts.ImageTaskError(ex.getCode(), ex.getMessage(), ex.isRetryable()),
                    "Retry cancellation later or query the task status.");
        }
    }

    public ImageContracts.CallbackResponse applyCallback(String rawPayload) {
        CallbackEvent event = parseCallback(rawPayload);
        ImageTask task = findTaskForCallback(event);
        if (task == null) {
            return new ImageContracts.CallbackResponse(false, event.localTaskId(), event.providerTaskId(), null, "Task not found.");
        }

        String eventKey = event.eventKey();
        if (!repository.markCallbackEventProcessed(eventKey)) {
            return new ImageContracts.CallbackResponse(true, task.taskId(), task.providerTaskId(), task.status(), "Duplicate callback ignored.");
        }

        ImageTask updatedTask = task.withCallbackUpdate(
                event.status(),
                event.progress(),
                event.result(),
                event.error(),
                Instant.now());
        repository.save(updatedTask);
        return new ImageContracts.CallbackResponse(
                true,
                updatedTask.taskId(),
                updatedTask.providerTaskId(),
                updatedTask.status(),
                "Callback applied.");
    }

    private java.util.Optional<ImageTask> visibleTask(String taskId, OwnerIdentity owner) {
        return repository.findByTaskId(taskId)
                .filter(task -> owner.canManageAllTasks() || task.ownerSubject().equals(owner.subject()));
    }

    private ImageTask findTaskForCallback(CallbackEvent event) {
        if (StringUtils.hasText(event.localTaskId())) {
            ImageTask task = repository.findByTaskId(event.localTaskId()).orElse(null);
            if (task != null) {
                return task;
            }
        }
        return repository.findByProviderTaskId(event.providerTaskId()).orElse(null);
    }

    private CallbackEvent parseCallback(String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode data = root.path("data").isMissingNode() ? root : root.path("data");
            String providerTaskId = firstText(data, "providerTaskId", "provider_task_id", "taskId", "task_id", "id");
            String localTaskId = firstText(data, "localTaskId", "local_task_id");
            if (!StringUtils.hasText(localTaskId)) {
                localTaskId = firstText(data.path("metadata"), "localTaskId", "local_task_id");
            }
            String eventId = firstText(root, "eventId", "event_id", "id");
            String statusText = firstText(data, "status", "state");
            GenerationTaskStatus status = GenerationTaskStatus.fromProviderStatus(statusText);
            Integer progress = firstInteger(data, "progress", "percent");
            ImageContracts.ImageResult result = parseResult(data);
            ImageContracts.ImageTaskError error = parseError(data, status);
            return new CallbackEvent(
                    StringUtils.hasText(eventId) ? eventId : fallbackEventKey(providerTaskId, statusText, rawPayload),
                    localTaskId,
                    providerTaskId,
                    status,
                    progress,
                    result,
                    error);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid callback JSON payload", ex);
        }
    }

    private ImageContracts.ImageResult parseResult(JsonNode data) {
        JsonNode resultNode = data.path("result").isMissingNode() ? data : data.path("result");
        List<String> imageUrls = imageUrls(resultNode);
        Instant expiresAt = parseInstant(firstText(resultNode, "expiresAt", "expires_at"));
        if (imageUrls.isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = objectMapper.convertValue(resultNode, Map.class);
        return new ImageContracts.ImageResult(imageUrls, expiresAt, metadata);
    }

    private ImageContracts.ImageTaskError parseError(JsonNode data, GenerationTaskStatus status) {
        if (status != GenerationTaskStatus.FAILED) {
            return null;
        }
        JsonNode errorNode = data.path("error").isMissingNode() ? data : data.path("error");
        String code = firstText(errorNode, "code", "errorCode", "error_code");
        String message = firstText(errorNode, "message", "errorMessage", "error_message");
        return new ImageContracts.ImageTaskError(
                StringUtils.hasText(code) ? code : "KLING_IMAGE_TASK_FAILED",
                StringUtils.hasText(message) ? message : "Kling image generation failed",
                false);
    }

    private OwnerIdentity currentOwner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Jwt jwt = extractJwt(authentication);
        String subject = jwt != null ? jwt.getSubject() : authentication.getName();
        if (!StringUtils.hasText(subject)) {
            return null;
        }
        String clientId = jwt == null ? null : jwt.getClaimAsString("client_id");
        String organizationId = jwt == null ? null : jwt.getClaimAsString("organization_id");
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
        return new OwnerIdentity(subject, clientId, organizationId, authorities);
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    private static ImageContracts.ImageTaskResponse unauthenticatedResponse() {
        return ImageContracts.ImageTaskResponse.error(
                "UNAUTHENTICATED",
                "Authentication is required",
                false,
                "Authenticate with a token that has Kling MCP scopes.");
    }

    private static ImageContracts.ImageTaskResponse taskNotFoundResponse() {
        return ImageContracts.ImageTaskResponse.error(
                "TASK_NOT_FOUND",
                "Image task was not found",
                false,
                "Check the taskId and ensure it belongs to the current user.");
    }

    private static String nextActionFor(ImageTask task) {
        return switch (task.status()) {
            case SUBMITTED, RUNNING -> "Call get_image_task or wait_for_image_task later.";
            case SUCCEEDED -> "Use the result.imageUrls before they expire.";
            case FAILED -> "Review error and submit a corrected create_image request.";
            case CANCELLED, EXPIRED -> "Create a new image task if needed.";
        };
    }

    private static void sleepOneSecond() {
        try {
            Thread.sleep(1_000);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static String fallbackEventKey(String providerTaskId, String status, String rawPayload) {
        String keyMaterial = (providerTaskId == null ? "" : providerTaskId)
                + ":"
                + (status == null ? "" : status)
                + ":"
                + rawPayload;
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
        }
        catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
        return HexFormat.of().formatHex(digest);
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

    private static Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        }
        catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    private static List<String> imageUrls(JsonNode resultNode) {
        List<String> urls = new ArrayList<>();
        addIfText(urls, resultNode.path("imageUrl"));
        addIfText(urls, resultNode.path("image_url"));
        addIfText(urls, resultNode.path("url"));
        addArrayUrls(urls, resultNode.path("imageUrls"));
        addArrayUrls(urls, resultNode.path("image_urls"));
        addArrayUrls(urls, resultNode.path("images"));
        return urls.stream().distinct().toList();
    }

    private static void addArrayUrls(List<String> urls, JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            return;
        }
        arrayNode.forEach(node -> {
            if (node.isTextual()) {
                addIfText(urls, node);
                return;
            }
            addIfText(urls, node.path("url"));
            addIfText(urls, node.path("imageUrl"));
            addIfText(urls, node.path("image_url"));
        });
    }

    private static void addIfText(List<String> urls, JsonNode node) {
        if (node.isTextual() && StringUtils.hasText(node.asText())) {
            urls.add(node.asText());
        }
    }

    private record CallbackEvent(
            String eventId,
            String localTaskId,
            String providerTaskId,
            GenerationTaskStatus status,
            Integer progress,
            ImageContracts.ImageResult result,
            ImageContracts.ImageTaskError error) {

        String eventKey() {
            return eventId;
        }
    }
}
