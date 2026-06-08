package com.klingai.poc.hello.klingmcp.video.model;

import com.klingai.poc.hello.klingmcp.auth.OwnerIdentity;

import java.time.Instant;

public record VideoTask(
        String taskId,
        String providerTaskId,
        String ownerSubject,
        String clientId,
        String organizationId,
        VideoContracts.CreateVideoRequest request,
        VideoTaskStatus status,
        Integer progress,
        VideoContracts.VideoResult result,
        VideoContracts.VideoTaskError error,
        Instant createdAt,
        Instant updatedAt) {

    public static VideoTask create(String taskId, OwnerIdentity owner, VideoContracts.CreateVideoRequest request, Instant now) {
        return new VideoTask(
                taskId,
                null,
                owner.subject(),
                owner.clientId(),
                owner.organizationId(),
                request,
                VideoTaskStatus.SUBMITTED,
                0,
                null,
                null,
                now,
                now);
    }

    public VideoTask withProviderSubmission(String providerTaskId, VideoTaskStatus status, Integer progress, Instant now) {
        return new VideoTask(
                taskId,
                providerTaskId,
                ownerSubject,
                clientId,
                organizationId,
                request,
                status,
                normalizeProgress(progress, this.progress),
                result,
                null,
                createdAt,
                now);
    }

    public VideoTask withCallbackUpdate(
            VideoTaskStatus newStatus,
            Integer newProgress,
            VideoContracts.VideoResult newResult,
            VideoContracts.VideoTaskError newError,
            Instant now) {
        if (status.isTerminal() && !status.equals(newStatus)) {
            return this;
        }
        Integer fallbackProgress = progressForStatus(newStatus) != null ? progressForStatus(newStatus) : progress;
        return new VideoTask(
                taskId,
                providerTaskId,
                ownerSubject,
                clientId,
                organizationId,
                request,
                newStatus,
                normalizeProgress(newProgress, fallbackProgress),
                newResult != null ? newResult : result,
                newError,
                createdAt,
                now);
    }

    public VideoTask withFailure(VideoContracts.VideoTaskError failure, Instant now) {
        return new VideoTask(
                taskId,
                providerTaskId,
                ownerSubject,
                clientId,
                organizationId,
                request,
                VideoTaskStatus.FAILED,
                progress,
                result,
                failure,
                createdAt,
                now);
    }

    public VideoTask withCancelled(Instant now) {
        return new VideoTask(
                taskId,
                providerTaskId,
                ownerSubject,
                clientId,
                organizationId,
                request,
                VideoTaskStatus.CANCELLED,
                progress,
                result,
                null,
                createdAt,
                now);
    }

    private static Integer normalizeProgress(Integer candidate, Integer fallback) {
        if (candidate == null) {
            return fallback;
        }
        return Math.max(0, Math.min(100, candidate));
    }

    private static Integer progressForStatus(VideoTaskStatus status) {
        return status == VideoTaskStatus.SUCCEEDED ? 100 : null;
    }
}
