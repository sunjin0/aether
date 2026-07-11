package com.aether.agent.service;

import com.aether.agent.entity.AgentConversation;
import com.aether.agent.vo.AgentConversationLifecycleVo;
import com.aether.agent.vo.AgentMessageStatisticsVo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 会话 Service 接口
 */
public interface AgentConversationService extends IService<AgentConversation> {

    /**
     * 获取会话生命周期信息
     */
    AgentConversationLifecycleVo getLifecycle(String conversationId);

    /**
     * 获取会话消息统计
     */
    AgentMessageStatisticsVo getStatistics(String conversationId);
}
