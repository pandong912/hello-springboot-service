package com.klingai.poc.hello.klingfeignclient;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.fasterxml.jackson.databind.JsonNode;

@FeignClient(name = "klingApi", url = "${kling.mcp.api.base-url:http://localhost:8089}")
public interface KlingFeignClient {

    @PostMapping(path = "${kling.mcp.api.create-video-path}", consumes = MediaType.APPLICATION_JSON_VALUE)
    JsonNode createVideo(
            @RequestHeader Map<String, String> headers,
            @RequestBody Map<String, Object> payload);

    @PostMapping(path = "${kling.mcp.api.cancel-video-path}")
    void cancelVideo(
            @RequestHeader Map<String, String> headers,
            @PathVariable("providerTaskId") String providerTaskId);

    @PostMapping(path = "${kling.mcp.api.create-image-path}", consumes = MediaType.APPLICATION_JSON_VALUE)
    JsonNode createImage(
            @RequestHeader Map<String, String> headers,
            @RequestBody Map<String, Object> payload);

    @PostMapping(path = "${kling.mcp.api.cancel-image-path}")
    void cancelImage(
            @RequestHeader Map<String, String> headers,
            @PathVariable("providerTaskId") String providerTaskId);
}
