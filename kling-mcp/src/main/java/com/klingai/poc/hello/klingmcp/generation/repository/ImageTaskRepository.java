package com.klingai.poc.hello.klingmcp.generation.repository;

import java.util.List;
import java.util.Optional;

import com.klingai.poc.hello.klingmcp.generation.model.ImageTask;
import com.klingai.poc.hello.klingmcp.generation.model.GenerationTaskStatus;

public interface ImageTaskRepository {

    ImageTask save(ImageTask task);

    Optional<ImageTask> findByTaskId(String taskId);

    Optional<ImageTask> findByProviderTaskId(String providerTaskId);

    List<ImageTask> findByOwner(String ownerSubject, GenerationTaskStatus status, int limit, String cursor);

    boolean markCallbackEventProcessed(String eventKey);
}
