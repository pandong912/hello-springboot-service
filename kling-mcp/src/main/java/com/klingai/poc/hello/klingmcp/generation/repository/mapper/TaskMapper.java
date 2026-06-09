package com.klingai.poc.hello.klingmcp.generation.repository.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.klingai.poc.hello.klingmcp.generation.repository.entity.TaskEntity;

@Mapper
public interface TaskMapper extends BaseMapper<TaskEntity> {
}
