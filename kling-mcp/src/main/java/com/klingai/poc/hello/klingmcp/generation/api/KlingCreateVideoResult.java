package com.klingai.poc.hello.klingmcp.generation.api;

import java.util.Map;

import com.klingai.poc.hello.klingmcp.generation.model.GenerationTaskStatus;

public record KlingCreateVideoResult(
        String providerTaskId,
        GenerationTaskStatus status,
        Integer progress,
        Map<String, Object> metadata) {
}
