package com.klingai.poc.hello.klingmcp.generation.model;

import java.time.Instant;

import com.klingai.poc.hello.klingmcp.auth.OwnerIdentity;

public record ImageTask(
        String taskId,
        String providerTaskId,
        String ownerSubject,
        String clientId,
        String organizationId,
        ImageContracts.CreateImageRequest request,
        GenerationTaskStatus status,
        Integer progress,
        ImageContracts.ImageResult result,
        ImageContracts.ImageTaskError error,
        Instant createdAt,
        Instant updatedAt) {

    public static ImageTask create(String taskId, OwnerIdentity owner, ImageContracts.CreateImageRequest request, Instant now) {
        return new ImageTask(
                taskId,
                null,
                owner.subject(),
                owner.clientId(),
                owner.organizationId(),
                request,
                GenerationTaskStatus.SUBMITTED,
                0,
                null,
                null,
                now,
                now);
    }

    public ImageTask withProviderSubmission(String providerTaskId, GenerationTaskStatus status, Integer progress, Instant now) {
        return new ImageTask(
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

    public ImageTask withCallbackUpdate(
            GenerationTaskStatus newStatus,
            Integer newProgress,
            ImageContracts.ImageResult newResult,
            ImageContracts.ImageTaskError newError,
            Instant now) {
        return withProviderUpdate(newStatus, newProgress, newResult, newError, now);
    }

    public ImageTask withProviderUpdate(
            GenerationTaskStatus newStatus,
            Integer newProgress,
            ImageContracts.ImageResult newResult,
            ImageContracts.ImageTaskError newError,
            Instant now) {
        if (status.isTerminal() && !status.equals(newStatus)) {
            return this;
        }
        Integer fallbackProgress = progressForStatus(newStatus) != null ? progressForStatus(newStatus) : progress;
        return new ImageTask(
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

    public ImageTask withFailure(ImageContracts.ImageTaskError failure, Instant now) {
        return new ImageTask(
                taskId,
                providerTaskId,
                ownerSubject,
                clientId,
                organizationId,
                request,
                GenerationTaskStatus.FAILED,
                progress,
                result,
                failure,
                createdAt,
                now);
    }

    public ImageTask withCancelled(Instant now) {
        return new ImageTask(
                taskId,
                providerTaskId,
                ownerSubject,
                clientId,
                organizationId,
                request,
                GenerationTaskStatus.CANCELLED,
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

    private static Integer progressForStatus(GenerationTaskStatus status) {
        return status == GenerationTaskStatus.SUCCEEDED ? 100 : null;
    }
}
