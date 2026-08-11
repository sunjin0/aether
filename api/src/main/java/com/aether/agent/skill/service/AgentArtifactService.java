package com.aether.agent.skill.service;

import com.aether.agent.skill.dto.AgentArtifactQueryDto;
import com.aether.agent.skill.entity.AgentArtifact;
import com.aether.agent.skill.vo.AgentArtifactVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentArtifactService extends IService<AgentArtifact> {
    Page<AgentArtifactVo> pageOwned(String userId, AgentArtifactQueryDto query);
    AgentArtifact requireOwned(String id, String userId, boolean recycled);
    void recycle(String id, String userId);
    void restore(String id, String userId);
    void purgeExpiredRecycled();
}
