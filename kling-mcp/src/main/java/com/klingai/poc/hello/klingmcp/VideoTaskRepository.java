package com.klingai.poc.hello.klingmcp;

import java.util.List;
import java.util.Optional;

public interface VideoTaskRepository {

    VideoTask save(VideoTask task);

    Optional<VideoTask> findByTaskId(String taskId);

    Optional<VideoTask> findByProviderTaskId(String providerTaskId);

    List<VideoTask> findByOwner(String ownerSubject, VideoTaskStatus status, int limit, String cursor);

    boolean markCallbackEventProcessed(String eventKey);
}
