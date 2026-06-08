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
@TableName("task_callback_events")
public class CallbackEventEntity {

    @TableId(value = "event_key", type = IdType.INPUT)
    private String eventKey;

    private Instant processedAt;
}
