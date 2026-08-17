package com.aether.agent.sandbox.mapper;

import com.aether.agent.sandbox.entity.SandboxExecutionEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供SandboxExecution事件映的数据访问能力。
 */
@Mapper
public interface SandboxExecutionEventMapper extends BaseMapper<SandboxExecutionEvent> {
}
