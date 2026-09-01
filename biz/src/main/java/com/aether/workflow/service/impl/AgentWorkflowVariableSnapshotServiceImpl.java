package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowVariableSnapshot;
import com.aether.workflow.mapper.AgentWorkflowVariableSnapshotMapper;
import com.aether.workflow.runtime.WorkflowSensitiveDataSanitizer;
import com.aether.workflow.service.AgentWorkflowVariableSnapshotService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import com.aether.local.CurrentUser;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 快照独立事务写入，审计数据不随节点业务回滚丢失。 */
@Service
public class AgentWorkflowVariableSnapshotServiceImpl extends ServiceImpl<AgentWorkflowVariableSnapshotMapper, AgentWorkflowVariableSnapshot>
        implements AgentWorkflowVariableSnapshotService {
    private final WorkflowSensitiveDataSanitizer sanitizer;

    public AgentWorkflowVariableSnapshotServiceImpl(WorkflowSensitiveDataSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void capture(String instanceId, String nodeInstanceId, String nodeId, String variables) {
        AgentWorkflowVariableSnapshot snapshot = new AgentWorkflowVariableSnapshot();
        snapshot.setTenantId(CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId"));
        snapshot.setInstanceId(instanceId); snapshot.setNodeInstanceId(nodeInstanceId); snapshot.setNodeId(nodeId);
        snapshot.setSnapshotStage("AFTER"); snapshot.setVariables(sanitizer.sanitizeJson(variables));
        save(snapshot);
    }
}
