package com.klingai.poc.hello.klingmcp;

import java.util.Map;

public record KlingCreateVideoResult(
        String providerTaskId,
        VideoTaskStatus status,
        Integer progress,
        Map<String, Object> metadata) {
}
