package com.aether.workflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * PostgreSQL 聚合查询，避免为运营面板把大量实例记录加载到内存。
 */
@Mapper
public interface AgentWorkflowMetricsMapper {
    /**
     * 处理instanceMetrics。
     */
    @Select("SELECT COUNT(*) AS total, " +
            "COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed, " +
            "COUNT(*) FILTER (WHERE status IN ('FAILED','TIMED_OUT','TERMINATED')) AS failed, " +
            "COUNT(*) FILTER (WHERE status = 'WAITING_USER') AS waiting, " +
            "COALESCE(AVG(completed_at - started_at) FILTER (WHERE status = 'COMPLETED' AND completed_at IS NOT NULL AND started_at IS NOT NULL), 0) AS completed_duration, " +
            "COALESCE(AVG(EXTRACT(EPOCH FROM clock_timestamp()) * 1000 - started_at) FILTER (WHERE status = 'WAITING_USER' AND started_at IS NOT NULL), 0) AS waiting_duration " +
            "FROM agent_workflow_instance WHERE deleted = FALSE AND (CAST(#{tenantId} AS varchar) IS NULL OR tenant_id = CAST(#{tenantId} AS varchar))")
    Map<String, Object> instanceMetrics(@Param("tenantId") String tenantId);

    /**
     * 处理nodeMetrics。
     */
    @Select("SELECT COALESCE(AVG(completed_at - started_at) FILTER (WHERE completed_at IS NOT NULL AND started_at IS NOT NULL), 0) AS node_duration, " +
            "COUNT(*) FILTER (WHERE node_type = 'tool' AND status = 'FAILED') AS mcp_failed " +
            "FROM agent_workflow_node_instance WHERE deleted = FALSE AND (CAST(#{tenantId} AS varchar) IS NULL OR tenant_id = CAST(#{tenantId} AS varchar))")
    Map<String, Object> nodeMetrics(@Param("tenantId") String tenantId);

    /**
     * 回调Metrics。
     */
    @Select("SELECT COUNT(*) AS callback_failed FROM agent_workflow_callback_delivery WHERE deleted = FALSE AND status = 'FAILED' AND (CAST(#{tenantId} AS varchar) IS NULL OR tenant_id = CAST(#{tenantId} AS varchar))")
    Map<String, Object> callbackMetrics(@Param("tenantId") String tenantId);

    /**
     * 处理executionMetrics。
     */
    @Select("SELECT COUNT(*) AS execution_dead_letter FROM agent_workflow_execution_job WHERE deleted = FALSE AND status = 'FAILED' AND (CAST(#{tenantId} AS varchar) IS NULL OR tenant_id = CAST(#{tenantId} AS varchar))")
    Map<String, Object> executionMetrics(@Param("tenantId") String tenantId);
}
