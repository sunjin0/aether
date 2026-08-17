package com.aether.agent.skill.service;

import com.aether.agent.skill.dto.AgentArtifactQueryDto;
import com.aether.agent.skill.entity.AgentArtifact;
import com.aether.agent.skill.vo.AgentArtifactVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 定义智能体Artifact业务服务契约。
 */
public interface AgentArtifactService extends IService<AgentArtifact> {
    /**
     * 分页查询Owned。
     */
    Page<AgentArtifactVo> pageOwned(String userId, AgentArtifactQueryDto query);

    /**
     * 处理requireOwned。
     */
    AgentArtifact requireOwned(String id, String userId, boolean recycled);

    /**
     * 处理recycle。
     */
    void recycle(String id, String userId);

    /**
     * 处理restore。
     */
    void restore(String id, String userId);

    /**
     * 处理purgeExpiredRecycled。
     */
    void purgeExpiredRecycled();

    /**
     * 处理purgeExpiredArtifacts。
     */
    void purgeExpiredArtifacts();
}
