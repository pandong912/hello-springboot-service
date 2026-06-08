package com.klingai.poc.hello.klingmcp.video.api;

import com.klingai.poc.hello.klingmcp.video.model.VideoTask;
import com.klingai.poc.hello.klingmcp.video.model.VideoTaskStatus;

import java.util.Map;

public record KlingCreateVideoResult(
        String providerTaskId,
        VideoTaskStatus status,
        Integer progress,
        Map<String, Object> metadata) {
}
