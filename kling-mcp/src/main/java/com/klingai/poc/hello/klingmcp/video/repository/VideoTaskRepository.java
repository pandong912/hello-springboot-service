package com.klingai.poc.hello.klingmcp.video.repository;

import com.klingai.poc.hello.klingmcp.video.model.VideoTask;
import com.klingai.poc.hello.klingmcp.video.model.VideoTaskStatus;

import java.util.List;
import java.util.Optional;

public interface VideoTaskRepository {

    VideoTask save(VideoTask task);

    Optional<VideoTask> findByTaskId(String taskId);

    Optional<VideoTask> findByProviderTaskId(String providerTaskId);

    List<VideoTask> findByOwner(String ownerSubject, VideoTaskStatus status, int limit, String cursor);

    boolean markCallbackEventProcessed(String eventKey);
}
