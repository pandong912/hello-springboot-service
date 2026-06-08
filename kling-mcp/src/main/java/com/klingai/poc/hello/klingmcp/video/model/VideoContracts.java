package com.klingai.poc.hello.klingmcp.video.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class VideoContracts {

    private VideoContracts() {
    }

    public record CreateVideoRequest(
            String prompt,
            String model,
            Integer durationSeconds,
            String aspectRatio,
            String negativePrompt,
            Long seed,
            String imageUrl,
            String idempotencyKey) {
    }

    public record GetVideoTaskRequest(String taskId) {
    }

    public record WaitVideoTaskRequest(String taskId, Integer timeoutSeconds) {
    }

    public record CancelVideoTaskRequest(String taskId) {
    }

    public record ListVideoTasksRequest(VideoTaskStatus status, Integer limit, String cursor) {
    }

    public record VideoResult(
            String videoUrl,
            String coverUrl,
            Instant expiresAt,
            Map<String, Object> metadata) {
    }

    public record VideoTaskError(String code, String message, boolean retryable) {
    }

    public record VideoTaskResponse(
            boolean ok,
            String taskId,
            String providerTaskId,
            VideoTaskStatus status,
            Instant createdAt,
            Instant updatedAt,
            Integer progress,
            VideoResult result,
            VideoTaskError error,
            String nextAction) {

        public static VideoTaskResponse fromTask(VideoTask task, String nextAction) {
            return new VideoTaskResponse(
                    true,
                    task.taskId(),
                    task.providerTaskId(),
                    task.status(),
                    task.createdAt(),
                    task.updatedAt(),
                    task.progress(),
                    task.result(),
                    task.error(),
                    nextAction);
        }

        public static VideoTaskResponse error(String code, String message, boolean retryable, String nextAction) {
            return new VideoTaskResponse(
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new VideoTaskError(code, message, retryable),
                    nextAction);
        }
    }

    public record VideoTaskSummary(
            String taskId,
            String providerTaskId,
            VideoTaskStatus status,
            Instant createdAt,
            Instant updatedAt,
            Integer progress,
            String prompt) {

        public static VideoTaskSummary fromTask(VideoTask task) {
            return new VideoTaskSummary(
                    task.taskId(),
                    task.providerTaskId(),
                    task.status(),
                    task.createdAt(),
                    task.updatedAt(),
                    task.progress(),
                    task.request().prompt());
        }
    }

    public record ListVideoTasksResponse(
            boolean ok,
            List<VideoTaskSummary> tasks,
            String nextCursor,
            VideoTaskError error) {

        public static ListVideoTasksResponse ok(List<VideoTaskSummary> tasks, String nextCursor) {
            return new ListVideoTasksResponse(true, tasks, nextCursor, null);
        }

        public static ListVideoTasksResponse error(String code, String message, boolean retryable) {
            return new ListVideoTasksResponse(false, List.of(), null, new VideoTaskError(code, message, retryable));
        }
    }

    public record CallbackResponse(
            boolean ok,
            String taskId,
            String providerTaskId,
            VideoTaskStatus status,
            String message) {
    }
}
