package com.klingai.poc.hello.klingmcp.generation.repository;

import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klingai.poc.hello.klingmcp.generation.model.ImageContracts;
import com.klingai.poc.hello.klingmcp.generation.model.ImageTask;
import com.klingai.poc.hello.klingmcp.generation.model.GenerationTaskStatus;
import com.klingai.poc.hello.klingmcp.generation.repository.entity.CallbackEventEntity;
import com.klingai.poc.hello.klingmcp.generation.repository.entity.TaskEntity;
import com.klingai.poc.hello.klingmcp.generation.repository.mapper.CallbackEventMapper;
import com.klingai.poc.hello.klingmcp.generation.repository.mapper.TaskMapper;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kling.mcp.repository.type", havingValue = "postgres", matchIfMissing = true)
public class PostgresImageTaskRepository implements ImageTaskRepository {

    private static final String TASK_TYPE = "image";

    private final TaskMapper taskMapper;
    private final CallbackEventMapper callbackEventMapper;
    private final ObjectMapper objectMapper;

    @Override
    public ImageTask save(ImageTask task) {
        TaskEntity entity = toEntity(task);
        if (taskMapper.updateById(entity) == 0) {
            taskMapper.insert(entity);
        }
        return task;
    }

    @Override
    public Optional<ImageTask> findByTaskId(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(taskMapper.selectOne(Wrappers.lambdaQuery(TaskEntity.class)
                        .eq(TaskEntity::getTaskId, taskId)
                        .eq(TaskEntity::getTaskType, TASK_TYPE)
                        .last("LIMIT 1")))
                .map(this::toDomain);
    }

    @Override
    public Optional<ImageTask> findByProviderTaskId(String providerTaskId) {
        if (!StringUtils.hasText(providerTaskId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(taskMapper.selectOne(Wrappers.lambdaQuery(TaskEntity.class)
                        .eq(TaskEntity::getTaskType, TASK_TYPE)
                        .eq(TaskEntity::getProviderTaskId, providerTaskId)
                        .last("LIMIT 1")))
                .map(this::toDomain);
    }

    @Override
    public List<ImageTask> findByOwner(String ownerSubject, GenerationTaskStatus status, int limit, String cursor) {
        Instant cursorTime = parseCursor(cursor);
        int normalizedLimit = Math.clamp(limit, 1, 100);
        return taskMapper.selectList(Wrappers.lambdaQuery(TaskEntity.class)
                        .eq(TaskEntity::getTaskType, TASK_TYPE)
                        .eq(TaskEntity::getOwnerSubject, ownerSubject)
                        .eq(status != null, TaskEntity::getStatus, status == null ? null : status.name())
                        .lt(cursorTime != null, TaskEntity::getUpdatedAt, cursorTime)
                        .orderByDesc(TaskEntity::getUpdatedAt)
                        .last("LIMIT " + normalizedLimit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean markCallbackEventProcessed(String eventKey) {
        if (!StringUtils.hasText(eventKey)) {
            return false;
        }
        try {
            callbackEventMapper.insert(new CallbackEventEntity("image:" + eventKey, Instant.now()));
            return true;
        }
        catch (DuplicateKeyException ex) {
            return false;
        }
    }

    private TaskEntity toEntity(ImageTask task) {
        return new TaskEntity(
                task.taskId(),
                TASK_TYPE,
                task.providerTaskId(),
                task.ownerSubject(),
                task.clientId(),
                task.organizationId(),
                writeJson(task.request()),
                task.status().name(),
                task.progress(),
                writeNullableJson(task.result()),
                writeNullableJson(task.error()),
                task.createdAt(),
                task.updatedAt());
    }

    private ImageTask toDomain(TaskEntity entity) {
        return new ImageTask(
                entity.getTaskId(),
                entity.getProviderTaskId(),
                entity.getOwnerSubject(),
                entity.getClientId(),
                entity.getOrganizationId(),
                readJson(entity.getRequestJson(), ImageContracts.CreateImageRequest.class),
                GenerationTaskStatus.valueOf(entity.getStatus()),
                entity.getProgress(),
                readNullableJson(entity.getResultJson(), ImageContracts.ImageResult.class),
                readNullableJson(entity.getErrorJson(), ImageContracts.ImageTaskError.class),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private String writeNullableJson(Object value) {
        return value == null ? null : writeJson(value);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize image task data", ex);
        }
    }

    private <T> T readNullableJson(String json, Class<T> type) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        return readJson(json, type);
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize image task data", ex);
        }
    }

    private static Instant parseCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        try {
            return Instant.parse(cursor);
        }
        catch (DateTimeParseException ex) {
            return null;
        }
    }
}
