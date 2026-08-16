package com.aether.agent.service.impl;

import com.aether.agent.entity.*;
import com.aether.agent.mapper.*;
import com.aether.agent.service.AgentRunPlanService;
import com.aether.agent.vo.AgentRunPlanVo;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** 将公开计划投影为可审计、可恢复的版本化步骤。 */
@Service
public class AgentRunPlanServiceImpl implements AgentRunPlanService {
    private final AgentRunPlanMapper plans;
    private final AgentRunPlanVersionMapper versions;
    private final AgentRunPlanStepMapper steps;

    public AgentRunPlanServiceImpl(AgentRunPlanMapper plans, AgentRunPlanVersionMapper versions,
                                   AgentRunPlanStepMapper steps) {
        this.plans = plans; this.versions = versions; this.steps = steps;
    }

    @Override public void recordPlan(String runId, String reason, String summary, String snapshot) {
        recordPlan(runId, null, reason, summary, snapshot);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void recordPlan(String runId, String taskId, String reason, String summary, String snapshot) {
        long now = System.currentTimeMillis();
        // Task 是长期工作单元，Run 只是其中一次尝试；继续/目标变更必须沿用同一计划历史。
        AgentRunPlan plan = StringUtils.isBlank(taskId) ? null : plans.selectOne(Wrappers.lambdaQuery(AgentRunPlan.class)
                .eq(AgentRunPlan::getTaskId, taskId).eq(AgentRunPlan::getDeleted, false)
                .orderByDesc(AgentRunPlan::getUpdatedAt).last("LIMIT 1"));
        if (plan == null) {
            plan = plans.selectOne(Wrappers.lambdaQuery(AgentRunPlan.class).eq(AgentRunPlan::getRunId, runId));
        }
        if (plan == null) {
            plan = new AgentRunPlan(); plan.setRunId(runId); plan.setTaskId(taskId); plan.setCurrentVersion(0); plans.insert(plan);
        } else {
            boolean changed = false;
            if (taskId != null && plan.getTaskId() == null) { plan.setTaskId(taskId); changed = true; }
            if (!runId.equals(plan.getRunId())) { plan.setRunId(runId); changed = true; }
            if (changed) plans.updateById(plan);
        }
        List<AgentRunPlanVersion> history = versions.selectList(Wrappers.lambdaQuery(AgentRunPlanVersion.class)
                .eq(AgentRunPlanVersion::getPlanId, plan.getId()).orderByAsc(AgentRunPlanVersion::getVersion));
        AgentRunPlanVersion priorVersion = history.isEmpty() ? null : history.get(history.size() - 1);
        List<AgentRunPlanStep> priorSteps = priorVersion == null ? Collections.emptyList() : steps.selectList(
                Wrappers.lambdaQuery(AgentRunPlanStep.class).eq(AgentRunPlanStep::getPlanVersionId, priorVersion.getId())
                        .orderByAsc(AgentRunPlanStep::getSequence));
        Map<String, AgentRunPlanStep> priorByKey = priorSteps.stream().filter(step -> StringUtils.isNotBlank(step.getStepKey()))
                .collect(Collectors.toMap(AgentRunPlanStep::getStepKey, step -> step, (left, right) -> left));
        JSONObject parsed = parseSnapshot(snapshot);
        JSONArray tasks = parsed.getJSONArray("tasks");
        int versionNo = (plan.getCurrentVersion() == null ? 0 : plan.getCurrentVersion()) + 1;
        AgentRunPlanVersion version = new AgentRunPlanVersion();
        version.setPlanId(plan.getId()); version.setVersion(versionNo); version.setReason(normalizeReason(reason));
        version.setSummary(StringUtils.abbreviate(StringUtils.defaultString(summary), 500)); version.setSnapshot(parsed.toJSONString());
        versions.insert(version);

        List<AgentRunPlanStep> currentSteps = new ArrayList<AgentRunPlanStep>();
        if (tasks != null) for (int index = 0; index < tasks.size(); index++) {
            JSONObject task = tasks.getJSONObject(index);
            String key = StringUtils.defaultIfBlank(task.getString("id"), "step-" + (index + 1));
            AgentRunPlanStep prior = priorByKey.get(key);
            AgentRunPlanStep step = new AgentRunPlanStep();
            step.setPlanVersionId(version.getId()); step.setStepKey(key); step.setSequence(index + 1);
            step.setTitle(StringUtils.abbreviate(StringUtils.defaultIfBlank(task.getString("title"), "步骤 " + (index + 1)), 500));
            step.setStatus(resolveStatus(task.getString("status"), prior));
            step.setResultSummary(StringUtils.abbreviate(StringUtils.defaultIfBlank(task.getString("resultSummary"), prior == null ? null : prior.getResultSummary()), 2000));
            step.setIdempotencyKey(StringUtils.defaultIfBlank(task.getString("idempotencyKey"), stableStepKey(taskId, runId, key)));
            step.setAttemptCount(task.getInteger("attemptCount") == null ? (prior == null || prior.getAttemptCount() == null ? 0 : prior.getAttemptCount()) : task.getInteger("attemptCount"));
            step.setStartedAt(task.getLong("startedAt"));
            step.setCompletedAt("COMPLETED".equals(step.getStatus()) ? (task.getLong("completedAt") == null ? now : task.getLong("completedAt")) : null);
            steps.insert(step); currentSteps.add(step);
        }
        // 旧版本中未完成的步骤明确失效，恢复时不会重放过时副作用。
        for (AgentRunPlanStep prior : priorSteps) if (!"COMPLETED".equals(prior.getStatus())) {
            prior.setStatus("SUPERSEDED"); steps.updateById(prior);
        }
        plan.setCurrentVersion(versionNo); plan.setStatus("RUNNING"); plan.setLastActiveAt(now);
        AgentRunPlanStep current = currentSteps.stream().filter(step -> !"COMPLETED".equals(step.getStatus()))
                .min(Comparator.comparing(AgentRunPlanStep::getSequence)).orElse(null);
        plan.setCurrentStepId(current == null ? null : current.getId()); plans.updateById(plan);
    }

    private JSONObject parseSnapshot(String snapshot) {
        try { JSONObject result = JSON.parseObject(snapshot); return result == null ? new JSONObject() : result; }
        catch (Exception ignored) { return new JSONObject(); }
    }
    private String normalizeReason(String reason) {
        return Arrays.asList("INITIAL", "TOOL_RESULT", "USER_INPUT", "GOAL_CHANGED", "STEP_FAILED", "RESUME", "COMPLETED").contains(reason) ? reason : "OBSERVATION";
    }
    private String resolveStatus(String incoming, AgentRunPlanStep prior) {
        return prior != null && "COMPLETED".equals(prior.getStatus()) ? "COMPLETED" : StringUtils.defaultIfBlank(incoming, "PENDING");
    }
    private String stableStepKey(String taskId, String runId, String stepKey) { return StringUtils.defaultIfBlank(taskId, runId) + ":" + stepKey; }

    @Override public void markPaused(String runId, String reason) { updateStatus(runId, "PAUSED", reason); }
    @Override public void markRunning(String runId) { updateStatus(runId, "RUNNING", null); }

    /** 将 step.verified 回调的验证结论写入当前计划版本的对应步骤。 */
    @Override @Transactional(rollbackFor = Exception.class)
    public void markStepVerified(String runId, Integer stepIndex, String verification) {
        if (stepIndex == null || stepIndex < 1) return;
        AgentRunPlan plan = StringUtils.isBlank(runId) ? null : plans.selectOne(
                Wrappers.lambdaQuery(AgentRunPlan.class).eq(AgentRunPlan::getRunId, runId));
        if (plan == null || plan.getCurrentVersion() == null) return;
        AgentRunPlanVersion version = versions.selectOne(Wrappers.lambdaQuery(AgentRunPlanVersion.class)
                .eq(AgentRunPlanVersion::getPlanId, plan.getId())
                .eq(AgentRunPlanVersion::getVersion, plan.getCurrentVersion()));
        if (version == null) return;
        List<AgentRunPlanStep> rows = steps.selectList(Wrappers.lambdaQuery(AgentRunPlanStep.class)
                .eq(AgentRunPlanStep::getPlanVersionId, version.getId()).orderByAsc(AgentRunPlanStep::getSequence));
        if (stepIndex > rows.size()) return;
        AgentRunPlanStep update = new AgentRunPlanStep();
        update.setId(rows.get(stepIndex - 1).getId());
        update.setResultSummary(StringUtils.abbreviate(StringUtils.defaultString(verification), 2000));
        update.setStatus("COMPLETED");
        steps.updateById(update);
    }
    /** 将 step.started 回调标记的步骤置为执行中（PENDING→RUNNING）；已完成步骤不改写。 */
    @Override @Transactional(rollbackFor = Exception.class)
    public void markStepRunning(String runId, Integer stepIndex) {
        if (stepIndex == null || stepIndex < 1) return;
        AgentRunPlan plan = StringUtils.isBlank(runId) ? null : plans.selectOne(
                Wrappers.lambdaQuery(AgentRunPlan.class).eq(AgentRunPlan::getRunId, runId));
        if (plan == null || plan.getCurrentVersion() == null) return;
        AgentRunPlanVersion version = versions.selectOne(Wrappers.lambdaQuery(AgentRunPlanVersion.class)
                .eq(AgentRunPlanVersion::getPlanId, plan.getId())
                .eq(AgentRunPlanVersion::getVersion, plan.getCurrentVersion()));
        if (version == null) return;
        List<AgentRunPlanStep> rows = steps.selectList(Wrappers.lambdaQuery(AgentRunPlanStep.class)
                .eq(AgentRunPlanStep::getPlanVersionId, version.getId()).orderByAsc(AgentRunPlanStep::getSequence));
        if (stepIndex > rows.size()) return;
        AgentRunPlanStep target = rows.get(stepIndex - 1);
        if ("COMPLETED".equals(target.getStatus()) || "FAILED".equals(target.getStatus())
                || "SUPERSEDED".equals(target.getStatus())) return;
        AgentRunPlanStep update = new AgentRunPlanStep();
        update.setId(target.getId());
        update.setStatus("RUNNING");
        update.setStartedAt(System.currentTimeMillis());
        steps.updateById(update);
    }
    private void updateStatus(String runId, String status, String reason) {
        AgentRunPlan plan = plans.selectOne(Wrappers.lambdaQuery(AgentRunPlan.class).eq(AgentRunPlan::getRunId, runId));
        if (plan != null) { plan.setStatus(status); plan.setPauseReason(reason); plan.setLastActiveAt(System.currentTimeMillis()); plans.updateById(plan); }
    }

    @Override public AgentRunPlanVo detail(String runId) {
        AgentRunPlan plan = plans.selectOne(Wrappers.lambdaQuery(AgentRunPlan.class).eq(AgentRunPlan::getRunId, runId));
        if (plan == null) return null;
        AgentRunPlanVo view = new AgentRunPlanVo(); view.setRunId(runId); view.setStatus(plan.getStatus()); view.setPauseReason(plan.getPauseReason());
        view.setCurrentVersion(plan.getCurrentVersion()); view.setCurrentStepId(plan.getCurrentStepId()); view.setLastActiveAt(plan.getLastActiveAt());
        view.setVersions(versions.selectList(Wrappers.lambdaQuery(AgentRunPlanVersion.class).eq(AgentRunPlanVersion::getPlanId, plan.getId()).orderByAsc(AgentRunPlanVersion::getVersion))
                .stream().map(this::toView).collect(Collectors.toList())); return view;
    }
    private AgentRunPlanVo.Version toView(AgentRunPlanVersion version) {
        AgentRunPlanVo.Version view = new AgentRunPlanVo.Version(); view.setVersion(version.getVersion()); view.setReason(version.getReason()); view.setSummary(version.getSummary());
        // snapshot 保留 plan.updated 的完整 {complex, document, tasks}，供 Dashboard 刷新后恢复方案文档渲染。
        JSONObject snapshot = parseSnapshot(version.getSnapshot());
        if (snapshot.getBoolean("complex") != null) view.setComplex(snapshot.getBoolean("complex"));
        if (StringUtils.isNotBlank(snapshot.getString("document"))) view.setDocument(snapshot.getString("document"));
        view.setSteps(steps.selectList(Wrappers.lambdaQuery(AgentRunPlanStep.class).eq(AgentRunPlanStep::getPlanVersionId, version.getId()).orderByAsc(AgentRunPlanStep::getSequence)).stream().map(step -> {
            AgentRunPlanVo.Step item = new AgentRunPlanVo.Step(); item.setId(step.getId()); item.setStepKey(step.getStepKey()); item.setSequence(step.getSequence()); item.setTitle(step.getTitle()); item.setStatus(step.getStatus()); item.setResultSummary(step.getResultSummary()); item.setAttemptCount(step.getAttemptCount()); item.setStartedAt(step.getStartedAt()); item.setCompletedAt(step.getCompletedAt()); return item;
        }).collect(Collectors.toList())); return view;
    }
    @Override public AgentRunPlanVo detailByTaskId(String taskId) {
        AgentRunPlan plan = plans.selectOne(Wrappers.lambdaQuery(AgentRunPlan.class).eq(AgentRunPlan::getTaskId, taskId).eq(AgentRunPlan::getDeleted, false).orderByDesc(AgentRunPlan::getUpdatedAt).last("LIMIT 1"));
        return plan == null ? null : detail(plan.getRunId());
    }
}
