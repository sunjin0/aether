package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentDocumentChunk;
import com.aether.agent.mapper.AgentDocumentChunkMapper;
import com.aether.agent.service.AgentDocumentChunkService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 文档分块 Service 实现。
 */
@Service
public class AgentDocumentChunkServiceImpl
        extends ServiceImpl<AgentDocumentChunkMapper, AgentDocumentChunk>
        implements AgentDocumentChunkService {
}
