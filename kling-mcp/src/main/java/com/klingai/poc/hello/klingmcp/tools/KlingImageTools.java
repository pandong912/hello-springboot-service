package com.klingai.poc.hello.klingmcp.tools;

import lombok.RequiredArgsConstructor;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klingai.poc.hello.klingmcp.generation.model.ImageContracts;
import com.klingai.poc.hello.klingmcp.generation.service.KlingImageService;

@Service
@RequiredArgsConstructor
public class KlingImageTools {

    private final KlingImageService imageService;
    private final ObjectMapper objectMapper;

    @Tool(name = "create_image", description = "Submit an asynchronous Kling image generation task and return a local task id.")
    public String createImage(ImageContracts.CreateImageRequest request) {
        return toJson(imageService.createImage(request));
    }

    @Tool(name = "get_image_task", description = "Return the current status and result for a Kling image generation task.")
    public String getImageTask(ImageContracts.GetImageTaskRequest request) {
        return toJson(imageService.getImageTask(request));
    }

    @Tool(name = "list_image_tasks", description = "List recent Kling image generation tasks owned by the current authenticated user.")
    public String listImageTasks(ImageContracts.ListImageTasksRequest request) {
        return toJson(imageService.listImageTasks(request));
    }

    @Tool(name = "wait_for_image_task", description = "Wait briefly for a Kling image task to reach a terminal status.")
    public String waitForImageTask(ImageContracts.WaitImageTaskRequest request) {
        return toJson(imageService.waitForImageTask(request));
    }

    @Tool(name = "cancel_image_task", description = "Cancel a Kling image generation task when the upstream API supports cancellation.")
    public String cancelImageTask(ImageContracts.CancelImageTaskRequest request) {
        return toJson(imageService.cancelImageTask(request));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize Kling image tool response", ex);
        }
    }
}
