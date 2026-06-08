package com.klingai.poc.hello.klingmcp.video.model;

import java.util.Locale;

public enum VideoTaskStatus {
    SUBMITTED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }

    public static VideoTaskStatus fromProviderStatus(String value) {
        if (value == null || value.isBlank()) {
            return SUBMITTED;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "submitted", "created", "pending", "queued" -> SUBMITTED;
            case "running", "processing", "in_progress", "generating" -> RUNNING;
            case "succeeded", "success", "completed", "complete", "done" -> SUCCEEDED;
            case "failed", "failure", "error" -> FAILED;
            case "cancelled", "canceled" -> CANCELLED;
            case "expired" -> EXPIRED;
            default -> RUNNING;
        };
    }
}
