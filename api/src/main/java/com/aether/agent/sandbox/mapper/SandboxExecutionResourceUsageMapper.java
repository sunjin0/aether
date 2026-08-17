package com.aether.agent.sandbox.mapper;

import com.aether.agent.sandbox.entity.SandboxExecutionResourceUsage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供SandboxExecution资源Usage映的数据访问能力。
 */
@Mapper
public interface SandboxExecutionResourceUsageMapper extends BaseMapper<SandboxExecutionResourceUsage> {
}
