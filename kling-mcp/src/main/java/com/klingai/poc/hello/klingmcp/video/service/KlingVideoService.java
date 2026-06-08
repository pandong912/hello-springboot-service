package com.klingai.poc.hello.klingmcp.video.service;

import com.klingai.poc.hello.klingmcp.auth.OwnerIdentity;
import com.klingai.poc.hello.klingmcp.config.KlingMcpProperties;
import com.klingai.poc.hello.klingmcp.video.api.KlingApiClient;
import com.klingai.poc.hello.klingmcp.video.api.KlingApiException;
import com.klingai.poc.hello.klingmcp.video.api.KlingCreateVideoResult;
import com.klingai.poc.hello.klingmcp.video.model.VideoContracts;
import com.klingai.poc.hello.klingmcp.video.model.VideoTask;
import com.klingai.poc.hello.klingmcp.video.model.VideoTaskStatus;
import com.klingai.poc.hello.klingmcp.video.repository.VideoTaskRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class KlingVideoService {

    private static final int DEFAULT_WAIT_SECONDS = 30;
    private static final int MAX_WAIT_SECONDS = 60;
    private static final int DEFAULT_LIST_LIMIT = 20;

    private final VideoTaskRepository repository;
    private final KlingApiClient klingApiClient;
    private final KlingMcpProperties properties;
    private final ObjectMapper objectMapper;

    public VideoContracts.VideoTaskResponse createVideo(VideoContracts.CreateVideoRequest request) {
        if (request == null || !StringUtils.hasText(request.prompt())) {
            return VideoContracts.VideoTaskResponse.error(
                    "INVALID_PROMPT",
                    "prompt is required",
                    false,
                    "Provide a non-empty prompt and call create_video again.");
        }

        OwnerIdentity owner = currentOwner();
        if (owner == null) {
            return unauthenticatedResponse();
        }

        Instant now = Instant.now();
        String taskId = UUID.randomUUID().toString();
        VideoTask task = repository.save(VideoTask.create(taskId, owner, request, now));

        try {
            KlingCreateVideoResult providerResult = klingApiClient.createVideo(
                    taskId,
                    request,
                    owner,
                    properties.callbackUri());
            task = task.withProviderSubmission(
                    providerResult.providerTaskId(),
                    providerResult.status(),
                    providerResult.progress(),
                    Instant.now());
            repository.save(task);
            return VideoContracts.VideoTaskResponse.fromTask(task, nextActionFor(task));
        }
        catch (KlingApiException ex) {
            task = task.withFailure(new VideoContracts.VideoTaskError(ex.getCode(), ex.getMessage(), ex.isRetryable()), Instant.now());
            repository.save(task);
            return VideoContracts.VideoTaskResponse.fromTask(task, "Fix the request or retry after the upstream API recovers.");
        }
    }

    public VideoContracts.VideoTaskResponse getVideoTask(VideoContracts.GetVideoTaskRequest request) {
        OwnerIdentity owner = currentOwner();
        if (owner == null) {
            return unauthenticatedResponse();
        }
        return visibleTask(request == null ? null : request.taskId(), owner)
                .map(task -> VideoContracts.VideoTaskResponse.fromTask(task, nextActionFor(task)))
                .orElseGet(() -> taskNotFoundResponse());
    }

    public VideoContracts.ListVideoTasksResponse listVideoTasks(VideoContracts.ListVideoTasksRequest request) {
        OwnerIdentity owner = currentOwner();
        if (owner == null) {
            return VideoContracts.ListVideoTasksResponse.error("UNAUTHENTICATED", "Authentication is required", false);
        }
        int limit = request == null || request.limit() == null ? DEFAULT_LIST_LIMIT : request.limit();
        VideoTaskStatus status = request == null ? null : request.status();
        String cursor = request == null ? null : request.cursor();
        List<VideoTask> tasks = repository.findByOwner(owner.subject(), status, limit, cursor);
        List<VideoContracts.VideoTaskSummary> summaries = tasks.stream()
                .map(VideoContracts.VideoTaskSummary::fromTask)
                .toList();
        String nextCursor = tasks.size() == Math.max(1, Math.min(100, limit))
                ? tasks.get(tasks.size() - 1).updatedAt().toString()
                : null;
        return VideoContracts.ListVideoTasksResponse.ok(summaries, nextCursor);
    }

    public VideoContracts.VideoTaskResponse waitForVideoTask(VideoContracts.WaitVideoTaskRequest request) {
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

        VideoTask task = visibleTask(request.taskId(), owner).orElse(null);
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

        return VideoContracts.VideoTaskResponse.fromTask(
                task,
                task.status().isTerminal()
                        ? nextActionFor(task)
                        : "Task is still running. Call get_video_task or wait_for_video_task again later.");
    }

    public VideoContracts.VideoTaskResponse cancelVideoTask(VideoContracts.CancelVideoTaskRequest request) {
        OwnerIdentity owner = currentOwner();
        if (owner == null) {
            return unauthenticatedResponse();
        }
        VideoTask task = visibleTask(request == null ? null : request.taskId(), owner).orElse(null);
        if (task == null) {
            return taskNotFoundResponse();
        }
        if (task.status().isTerminal()) {
            return VideoContracts.VideoTaskResponse.fromTask(task, "Task is already terminal and cannot be cancelled.");
        }
        try {
            klingApiClient.cancelVideo(task.providerTaskId());
            task = task.withCancelled(Instant.now());
            repository.save(task);
            return VideoContracts.VideoTaskResponse.fromTask(task, "Task cancellation has been requested.");
        }
        catch (KlingApiException ex) {
            return new VideoContracts.VideoTaskResponse(
                    false,
                    task.taskId(),
                    task.providerTaskId(),
                    task.status(),
                    task.createdAt(),
                    task.updatedAt(),
                    task.progress(),
                    task.result(),
                    new VideoContracts.VideoTaskError(ex.getCode(), ex.getMessage(), ex.isRetryable()),
                    "Retry cancellation later or query the task status.");
        }
    }

    public VideoContracts.CallbackResponse applyCallback(String rawPayload) {
        CallbackEvent event = parseCallback(rawPayload);
        VideoTask task = findTaskForCallback(event);
        if (task == null) {
            return new VideoContracts.CallbackResponse(false, event.localTaskId(), event.providerTaskId(), null, "Task not found.");
        }

        String eventKey = event.eventKey();
        if (!repository.markCallbackEventProcessed(eventKey)) {
            return new VideoContracts.CallbackResponse(true, task.taskId(), task.providerTaskId(), task.status(), "Duplicate callback ignored.");
        }

        VideoTask updatedTask = task.withCallbackUpdate(
                event.status(),
                event.progress(),
                event.result(),
                event.error(),
                Instant.now());
        repository.save(updatedTask);
        return new VideoContracts.CallbackResponse(
                true,
                updatedTask.taskId(),
                updatedTask.providerTaskId(),
                updatedTask.status(),
                "Callback applied.");
    }

    private java.util.Optional<VideoTask> visibleTask(String taskId, OwnerIdentity owner) {
        return repository.findByTaskId(taskId)
                .filter(task -> owner.canManageAllTasks() || task.ownerSubject().equals(owner.subject()));
    }

    private VideoTask findTaskForCallback(CallbackEvent event) {
        if (StringUtils.hasText(event.localTaskId())) {
            VideoTask task = repository.findByTaskId(event.localTaskId()).orElse(null);
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
            VideoTaskStatus status = VideoTaskStatus.fromProviderStatus(statusText);
            Integer progress = firstInteger(data, "progress", "percent");
            VideoContracts.VideoResult result = parseResult(data);
            VideoContracts.VideoTaskError error = parseError(data, status);
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

    private VideoContracts.VideoResult parseResult(JsonNode data) {
        JsonNode resultNode = data.path("result").isMissingNode() ? data : data.path("result");
        String videoUrl = firstText(resultNode, "videoUrl", "video_url", "url");
        String coverUrl = firstText(resultNode, "coverUrl", "cover_url", "cover");
        Instant expiresAt = parseInstant(firstText(resultNode, "expiresAt", "expires_at"));
        if (!StringUtils.hasText(videoUrl) && !StringUtils.hasText(coverUrl)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = objectMapper.convertValue(resultNode, Map.class);
        return new VideoContracts.VideoResult(videoUrl, coverUrl, expiresAt, metadata);
    }

    private VideoContracts.VideoTaskError parseError(JsonNode data, VideoTaskStatus status) {
        if (status != VideoTaskStatus.FAILED) {
            return null;
        }
        JsonNode errorNode = data.path("error").isMissingNode() ? data : data.path("error");
        String code = firstText(errorNode, "code", "errorCode", "error_code");
        String message = firstText(errorNode, "message", "errorMessage", "error_message");
        return new VideoContracts.VideoTaskError(
                StringUtils.hasText(code) ? code : "KLING_TASK_FAILED",
                StringUtils.hasText(message) ? message : "Kling video generation failed",
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

    private static VideoContracts.VideoTaskResponse unauthenticatedResponse() {
        return VideoContracts.VideoTaskResponse.error(
                "UNAUTHENTICATED",
                "Authentication is required",
                false,
                "Authenticate with a token that has Kling MCP scopes.");
    }

    private static VideoContracts.VideoTaskResponse taskNotFoundResponse() {
        return VideoContracts.VideoTaskResponse.error(
                "TASK_NOT_FOUND",
                "Video task was not found",
                false,
                "Check the taskId and ensure it belongs to the current user.");
    }

    private static String nextActionFor(VideoTask task) {
        return switch (task.status()) {
            case SUBMITTED, RUNNING -> "Call get_video_task or wait_for_video_task later.";
            case SUCCEEDED -> "Use the result.videoUrl before it expires.";
            case FAILED -> "Review error and submit a corrected create_video request.";
            case CANCELLED, EXPIRED -> "Create a new video task if needed.";
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

    private record CallbackEvent(
            String eventId,
            String localTaskId,
            String providerTaskId,
            VideoTaskStatus status,
            Integer progress,
            VideoContracts.VideoResult result,
            VideoContracts.VideoTaskError error) {

        String eventKey() {
            return eventId;
        }
    }
}
