package com.klingai.poc.hello.klingmcp.generation.repository.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("generation_tasks")
public class TaskEntity {

    @TableId(value = "task_id", type = IdType.INPUT)
    private String taskId;

    private String taskType;

    private String providerTaskId;

    private String ownerSubject;

    private String clientId;

    private String organizationId;

    private String requestJson;

    private String status;

    private Integer progress;

    private String resultJson;

    private String errorJson;

    private Instant createdAt;

    private Instant updatedAt;
}
