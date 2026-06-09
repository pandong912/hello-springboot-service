package com.klingai.poc.hello.klingmcp.generation.api;

import java.util.Map;

import com.klingai.poc.hello.klingmcp.generation.model.GenerationTaskStatus;
import com.klingai.poc.hello.klingmcp.generation.model.ImageContracts;

public record KlingImageTaskResult(
        String providerTaskId,
        GenerationTaskStatus status,
        Integer progress,
        ImageContracts.ImageResult result,
        ImageContracts.ImageTaskError error,
        Map<String, Object> metadata) {
}
