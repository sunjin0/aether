package com.aether.agent.skill.service;

import com.aether.agent.skill.dto.AgentArtifactQueryDto;
import com.aether.agent.skill.entity.AgentArtifact;
import com.aether.agent.skill.vo.AgentArtifactVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Collection;

/**
 * 定义智能体Artifact业务服务契约。
 */
public interface AgentArtifactService extends IService<AgentArtifact> {
    /**
     * 分页查询Owned。
     */
    Page<AgentArtifactVo> pageOwned(String userId, AgentArtifactQueryDto query);

    /**
     * 分页查询Owned，并在归属用户之外再按允许的智能体定义收敛。
     *
     * <p>外部接入方（Front 服务账号）能看到的文件取决于它当下被授权的智能体，授权撤销后
     * 该智能体生成的历史文件也不该再出现在列表里，所以列表查询要带上这层约束。</p>
     *
     * @param agentDefinitionIds 允许的智能体定义 ID；传 {@code null} 表示不按智能体收敛，
     *                           传空集合由调用方提前拦截（空集合会拼出非法 SQL）
     */
    Page<AgentArtifactVo> pageOwned(String userId, Collection<String> agentDefinitionIds, AgentArtifactQueryDto query);

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
