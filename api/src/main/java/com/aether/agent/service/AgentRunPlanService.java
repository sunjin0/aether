package com.aether.agent.service;
import com.aether.agent.vo.AgentRunPlanVo;
public interface AgentRunPlanService { void recordPlan(String runId, String reason, String summary, String snapshot); void recordPlan(String runId, String taskId, String reason, String summary, String snapshot); void markPaused(String runId, String reason); void markRunning(String runId); AgentRunPlanVo detail(String runId); AgentRunPlanVo detailByTaskId(String taskId); }
