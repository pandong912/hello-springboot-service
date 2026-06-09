package com.klingai.poc.hello.klingmcp.generation.repository.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.klingai.poc.hello.klingmcp.generation.repository.entity.CallbackEventEntity;

@Mapper
public interface CallbackEventMapper extends BaseMapper<CallbackEventEntity> {
}
