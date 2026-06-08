package com.klingai.poc.hello.klingmcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class KlingVideoTools {

    private final KlingVideoService videoService;
    private final ObjectMapper objectMapper;

    public KlingVideoTools(KlingVideoService videoService, ObjectMapper objectMapper) {
        this.videoService = videoService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "create_video", description = "Submit an asynchronous Kling video generation task and return a local task id.")
    public String createVideo(VideoContracts.CreateVideoRequest request) {
        return toJson(videoService.createVideo(request));
    }

    @Tool(name = "get_video_task", description = "Return the current status and result for a Kling video generation task.")
    public String getVideoTask(VideoContracts.GetVideoTaskRequest request) {
        return toJson(videoService.getVideoTask(request));
    }

    @Tool(name = "list_video_tasks", description = "List recent Kling video generation tasks owned by the current authenticated user.")
    public String listVideoTasks(VideoContracts.ListVideoTasksRequest request) {
        return toJson(videoService.listVideoTasks(request));
    }

    @Tool(name = "wait_for_video_task", description = "Wait briefly for a Kling video task to reach a terminal status.")
    public String waitForVideoTask(VideoContracts.WaitVideoTaskRequest request) {
        return toJson(videoService.waitForVideoTask(request));
    }

    @Tool(name = "cancel_video_task", description = "Cancel a Kling video generation task when the upstream API supports cancellation.")
    public String cancelVideoTask(VideoContracts.CancelVideoTaskRequest request) {
        return toJson(videoService.cancelVideoTask(request));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize Kling video tool response", ex);
        }
    }
}
