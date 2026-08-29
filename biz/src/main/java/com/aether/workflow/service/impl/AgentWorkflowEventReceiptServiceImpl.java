package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowEventReceipt;
import com.aether.workflow.mapper.AgentWorkflowEventReceiptMapper;
import com.aether.workflow.service.AgentWorkflowEventReceiptService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;

@Service
public class AgentWorkflowEventReceiptServiceImpl extends ServiceImpl<AgentWorkflowEventReceiptMapper, AgentWorkflowEventReceipt>
        implements AgentWorkflowEventReceiptService {
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean claim(String applicationId, String eventType, String eventId, String correlationKey) {
        long now = System.currentTimeMillis();
        return baseMapper.insertIgnore(IdWorker.getIdStr(), applicationId, eventType, eventId, correlationKey, now, now) > 0;
    }
}
