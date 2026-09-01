package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowCallbackDelivery;
import com.aether.workflow.mapper.AgentWorkflowCallbackDeliveryMapper;
import com.aether.workflow.service.AgentWorkflowCallbackDeliveryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import com.aether.local.CurrentUser;
import org.apache.commons.lang3.StringUtils;

/**
 * 实现智能体工作流回调Delivery业务服务。
 */
@Service
public class AgentWorkflowCallbackDeliveryServiceImpl
        extends ServiceImpl<AgentWorkflowCallbackDeliveryMapper, AgentWorkflowCallbackDelivery>
        implements AgentWorkflowCallbackDeliveryService {
    @Override
    public AgentWorkflowCallbackDelivery getById(java.io.Serializable id) {
        AgentWorkflowCallbackDelivery value = super.getById(id);
        String tenantId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
        if (value != null && StringUtils.isNotBlank(tenantId) && !tenantId.equals(value.getTenantId())) return null;
        return value;
    }
}
