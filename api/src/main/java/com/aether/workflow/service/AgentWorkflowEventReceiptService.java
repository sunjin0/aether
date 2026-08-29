package com.aether.workflow.service;

import com.aether.workflow.entity.AgentWorkflowEventReceipt;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentWorkflowEventReceiptService extends IService<AgentWorkflowEventReceipt> {
    /** 原子登记事件；false 表示同一事件已被接收。 */
    boolean claim(String applicationId, String eventType, String eventId, String correlationKey);
}
