package com.aether.workflow.mapper;

import com.aether.workflow.entity.AgentWorkflowEventReceipt;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentWorkflowEventReceiptMapper extends BaseMapper<AgentWorkflowEventReceipt> {
    @Insert("INSERT INTO agent_workflow_event_receipt (id, tenant_id, application_id, event_type, event_id, correlation_key, created_at, updated_at, sort_num, deleted, state) "
            + "VALUES (#{id}, #{tenantId}, #{applicationId}, #{eventType}, #{eventId}, #{correlationKey}, #{createdAt}, #{updatedAt}, 0, FALSE, 0) "
            + "ON CONFLICT (application_id, event_type, event_id) WHERE deleted = FALSE DO NOTHING")
    int insertIgnore(@Param("id") String id, @Param("tenantId") String tenantId, @Param("applicationId") String applicationId,
                     @Param("eventType") String eventType, @Param("eventId") String eventId,
                     @Param("correlationKey") String correlationKey, @Param("createdAt") Long createdAt,
                     @Param("updatedAt") Long updatedAt);
}
