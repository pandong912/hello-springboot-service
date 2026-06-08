package com.klingai.poc.hello.klingmcp;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class InMemoryVideoTaskRepository implements VideoTaskRepository {

    private final Map<String, VideoTask> tasksById = new ConcurrentHashMap<>();
    private final Map<String, String> taskIdsByProviderTaskId = new ConcurrentHashMap<>();
    private final Set<String> processedCallbackEvents = ConcurrentHashMap.newKeySet();

    @Override
    public VideoTask save(VideoTask task) {
        tasksById.put(task.taskId(), task);
        if (StringUtils.hasText(task.providerTaskId())) {
            taskIdsByProviderTaskId.put(task.providerTaskId(), task.taskId());
        }
        return task;
    }

    @Override
    public Optional<VideoTask> findByTaskId(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(tasksById.get(taskId));
    }

    @Override
    public Optional<VideoTask> findByProviderTaskId(String providerTaskId) {
        if (!StringUtils.hasText(providerTaskId)) {
            return Optional.empty();
        }
        String taskId = taskIdsByProviderTaskId.get(providerTaskId);
        if (taskId == null) {
            return Optional.empty();
        }
        return findByTaskId(taskId);
    }

    @Override
    public List<VideoTask> findByOwner(String ownerSubject, VideoTaskStatus status, int limit, String cursor) {
        return tasksById.values().stream()
                .filter(task -> task.ownerSubject().equals(ownerSubject))
                .filter(task -> status == null || task.status() == status)
                .filter(task -> !StringUtils.hasText(cursor) || task.updatedAt().toString().compareTo(cursor) < 0)
                .sorted(Comparator.comparing(VideoTask::updatedAt).reversed())
                .limit(Math.clamp(limit, 1, 100))
                .toList();
    }

    @Override
    public boolean markCallbackEventProcessed(String eventKey) {
        return processedCallbackEvents.add(eventKey);
    }
}
