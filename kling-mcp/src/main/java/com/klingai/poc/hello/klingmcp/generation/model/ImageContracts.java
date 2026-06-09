package com.klingai.poc.hello.klingmcp.generation.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ImageContracts {

    private ImageContracts() {
    }

    public record CreateImageRequest(
            String prompt,
            String model,
            String aspectRatio,
            String negativePrompt,
            Long seed,
            Integer count,
            String style,
            String referenceImageUrl,
            String idempotencyKey) {
    }

    public record GetImageTaskRequest(String taskId) {
    }

    public record GetProviderImageTaskRequest(String providerTaskId) {
    }

    public record WaitImageTaskRequest(String taskId, Integer timeoutSeconds) {
    }

    public record CancelImageTaskRequest(String taskId) {
    }

    public record ListImageTasksRequest(GenerationTaskStatus status, Integer limit, String cursor) {
    }

    public record ImageResult(
            List<String> imageUrls,
            Instant expiresAt,
            Map<String, Object> metadata) {
    }

    public record ImageTaskError(String code, String message, boolean retryable) {
    }

    public record ImageTaskResponse(
            boolean ok,
            String taskId,
            String providerTaskId,
            GenerationTaskStatus status,
            Instant createdAt,
            Instant updatedAt,
            Integer progress,
            ImageResult result,
            ImageTaskError error,
            String nextAction) {

        public static ImageTaskResponse fromTask(ImageTask task, String nextAction) {
            return new ImageTaskResponse(
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

        public static ImageTaskResponse error(String code, String message, boolean retryable, String nextAction) {
            return new ImageTaskResponse(
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new ImageTaskError(code, message, retryable),
                    nextAction);
        }
    }

    public record ImageTaskSummary(
            String taskId,
            String providerTaskId,
            GenerationTaskStatus status,
            Instant createdAt,
            Instant updatedAt,
            Integer progress,
            String prompt) {

        public static ImageTaskSummary fromTask(ImageTask task) {
            return new ImageTaskSummary(
                    task.taskId(),
                    task.providerTaskId(),
                    task.status(),
                    task.createdAt(),
                    task.updatedAt(),
                    task.progress(),
                    task.request().prompt());
        }
    }

    public record ListImageTasksResponse(
            boolean ok,
            List<ImageTaskSummary> tasks,
            String nextCursor,
            ImageTaskError error) {

        public static ListImageTasksResponse ok(List<ImageTaskSummary> tasks, String nextCursor) {
            return new ListImageTasksResponse(true, tasks, nextCursor, null);
        }

        public static ListImageTasksResponse error(String code, String message, boolean retryable) {
            return new ListImageTasksResponse(false, List.of(), null, new ImageTaskError(code, message, retryable));
        }
    }

    public record CallbackResponse(
            boolean ok,
            String taskId,
            String providerTaskId,
            GenerationTaskStatus status,
            String message) {
    }
}
