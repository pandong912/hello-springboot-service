package com.klingai.poc.hello.klingmcp.generation.repository;

import java.util.List;
import java.util.Optional;

import com.klingai.poc.hello.klingmcp.generation.model.VideoTask;
import com.klingai.poc.hello.klingmcp.generation.model.GenerationTaskStatus;

public interface VideoTaskRepository {

    VideoTask save(VideoTask task);

    Optional<VideoTask> findByTaskId(String taskId);

    Optional<VideoTask> findByProviderTaskId(String providerTaskId);

    List<VideoTask> findByOwner(String ownerSubject, GenerationTaskStatus status, int limit, String cursor);

    boolean markCallbackEventProcessed(String eventKey);
}
