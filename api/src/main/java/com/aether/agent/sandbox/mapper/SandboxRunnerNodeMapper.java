package com.aether.agent.sandbox.mapper;

import com.aether.agent.sandbox.entity.SandboxRunnerNode;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供SandboxRunnerNode映的数据访问能力。
 */
@Mapper
public interface SandboxRunnerNodeMapper extends BaseMapper<SandboxRunnerNode> {
}
