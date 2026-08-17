package com.aether.agent.sandbox.mapper;

import com.aether.agent.sandbox.entity.SandboxExecutionTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供SandboxExecution任务映的数据访问能力。
 */
@Mapper
public interface SandboxExecutionTaskMapper extends BaseMapper<SandboxExecutionTask> {
}
