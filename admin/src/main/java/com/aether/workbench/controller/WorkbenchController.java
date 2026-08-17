package com.aether.workbench.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.service.KnowledgeReviewTaskQueryService;
import com.aether.knowledge.vo.KnowledgeReviewTaskQueryVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskVo;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowNodeInstance;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.aether.workflow.service.AgentWorkflowInstanceService;
import com.aether.workflow.service.AgentWorkflowNodeInstanceService;
import com.aether.workflow.service.AgentWorkflowOperationsService;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.workflow.vo.AgentWorkflowDeadLetterVo;
import com.aether.workflow.vo.AgentWorkflowOperationsMetricsVo;
import com.aether.workflow.vo.AgentWorkflowVo;
import com.aether.workbench.vo.WorkbenchItemVo;
import com.aether.workbench.vo.WorkbenchOverviewVo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 提供Workbench相关的 REST 接口。
 */
@Api(tags = "工作台 API")
@RestController
@RequestMapping("/api/workbench")
@Permission(path = "/dashboard")
public class WorkbenchController {
    private static final int ITEM_LIMIT = 5;

    private final AgentWorkflowService workflowService;
    private final AgentWorkflowInstanceService instanceService;
    private final AgentWorkflowNodeInstanceService nodeInstanceService;
    private final AgentWorkflowExecutionService executionService;
    private final AgentWorkflowOperationsService operationsService;
    private final KnowledgeReviewTaskQueryService reviewTaskQueryService;

    /**
     * 创建 {@code WorkbenchController} 实例。
     */
    public WorkbenchController(AgentWorkflowService workflowService,
                               AgentWorkflowInstanceService instanceService,
                               AgentWorkflowNodeInstanceService nodeInstanceService,
                               AgentWorkflowExecutionService executionService,
                               AgentWorkflowOperationsService operationsService,
                               KnowledgeReviewTaskQueryService reviewTaskQueryService) {
        this.workflowService = workflowService;
        this.instanceService = instanceService;
        this.nodeInstanceService = nodeInstanceService;
        this.executionService = executionService;
        this.operationsService = operationsService;
        this.reviewTaskQueryService = reviewTaskQueryService;
    }

    /**
     * 工作台聚合概览。
     */
    @ApiOperation("工作台聚合概览")
    @GetMapping("/overview")
    public WebResponse<WorkbenchOverviewVo> overview() {
        String userId = currentUserId();
        boolean administrator = executionService.isAdministrator(userId);
        WorkbenchOverviewVo result = new WorkbenchOverviewVo();

        List<AgentWorkflowInstance> activeInstances = instanceService.list(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .in(AgentWorkflowInstance::getStatus, "RUNNING", "WAITING_USER")
                .eq(!administrator, AgentWorkflowInstance::getUserId, userId)
                .eq(AgentWorkflowInstance::getDeleted, false)
                .orderByDesc(AgentWorkflowInstance::getStartedAt)
                .last("LIMIT " + ITEM_LIMIT));
        long waitingCount = instanceService.count(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getStatus, "WAITING_USER")
                .eq(!administrator, AgentWorkflowInstance::getUserId, userId)
                .eq(AgentWorkflowInstance::getDeleted, false));
        long runningCount = instanceService.count(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getStatus, "RUNNING")
                .eq(!administrator, AgentWorkflowInstance::getUserId, userId)
                .eq(AgentWorkflowInstance::getDeleted, false));
        result.setWaitingWorkflowInstances(waitingCount);
        result.setRunningWorkflowInstances(runningCount);

        Map<String, String> workflowNames = activeInstances.isEmpty() ? Collections.<String, String>emptyMap()
                : workflowService.listByIds(activeInstances.stream().map(AgentWorkflowInstance::getWorkflowId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(AgentWorkflow::getId, AgentWorkflow::getName));
        Map<String, List<AgentWorkflowNodeInstance>> nodesByInstance = activeInstances.isEmpty()
                ? Collections.<String, List<AgentWorkflowNodeInstance>>emptyMap()
                : nodeInstanceService.list(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class)
                        .in(AgentWorkflowNodeInstance::getInstanceId, activeInstances.stream()
                                .map(AgentWorkflowInstance::getId).collect(Collectors.toList()))).stream()
                .collect(Collectors.groupingBy(AgentWorkflowNodeInstance::getInstanceId));
        result.setRunning(activeInstances.stream().map(instance -> workflowItem(instance, workflowNames.get(instance.getWorkflowId()),
                        nodesByInstance.get(instance.getId())))
                .collect(Collectors.toList()));

        KnowledgeReviewTaskQueryVo reviewQuery = new KnowledgeReviewTaskQueryVo();
        reviewQuery.setView("available");
        reviewQuery.setCurrent(1L);
        reviewQuery.setPageSize((long) ITEM_LIMIT);
        com.baomidou.mybatisplus.core.metadata.IPage<KnowledgeReviewTaskVo> reviews = reviewTaskQueryService.list(reviewQuery);
        result.setReviewTasks(reviews.getTotal());
        List<WorkbenchItemVo> pending = reviews.getRecords().stream().map(this::reviewItem).collect(Collectors.toList());
        pending.addAll(activeInstances.stream().filter(instance -> "WAITING_USER".equals(instance.getStatus()))
                .map(instance -> workflowItem(instance, workflowNames.get(instance.getWorkflowId()), nodesByInstance.get(instance.getId())))
                .collect(Collectors.toList()));
        pending.sort(Comparator.comparing(WorkbenchItemVo::isOverdue).reversed()
                .thenComparing(item -> "WAITING_USER".equals(item.getStatus()) ? 0 : 1)
                .thenComparing(WorkbenchItemVo::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        result.setPending(pending.stream().limit(ITEM_LIMIT).collect(Collectors.toList()));

        if (administrator) {
            AgentWorkflowOperationsMetricsVo metrics = operationsService.metrics();
            result.setFailedCallbacks(metrics.getCallbackFailedCount() == null ? 0 : metrics.getCallbackFailedCount());
            result.setExecutionDeadLetters(metrics.getExecutionDeadLetterCount() == null ? 0 : metrics.getExecutionDeadLetterCount());
            result.setAttention(operationsService.deadLetters(ITEM_LIMIT).stream().map(this::deadLetterItem)
                    .collect(Collectors.toList()));
        }

        List<AgentWorkflowVo> quickStarts = workflowService.list(Wrappers.lambdaQuery(AgentWorkflow.class)
                .eq(AgentWorkflow::getStatus, 1)
                .eq(AgentWorkflow::getDeleted, false)
                .orderByDesc(AgentWorkflow::getUpdatedAt)
                .last("LIMIT " + ITEM_LIMIT)).stream().map(workflow -> {
            AgentWorkflowVo vo = new AgentWorkflowVo();
            BeanUtils.copyProperties(workflow, vo);
            return vo;
        }).collect(Collectors.toList());
        result.setQuickStartWorkflows(quickStarts);
        return WebResponse.OK(result);
    }

    /**
     * 当前用户Id。
     */
    private String currentUserId() {
        Map<String, String> user = CurrentUser.getUser();
        String userId = user == null ? null : user.get("userId");
        if (StringUtils.isBlank(userId))
            throw new ServerException(401, I18nUtils.getMessage("auth.error.no.permission"));
        return userId;
    }

    /**
     * 工作流Item。
     */
    private WorkbenchItemVo workflowItem(AgentWorkflowInstance instance, String workflowName,
                                         List<AgentWorkflowNodeInstance> nodes) {
        WorkbenchItemVo item = new WorkbenchItemVo();
        item.setType("workflow-instance");
        item.setId(instance.getId());
        item.setWorkflowId(instance.getWorkflowId());
        item.setTitle(StringUtils.defaultIfBlank(workflowName, instance.getWorkflowId()));
        item.setStatus(instance.getStatus());
        item.setDescription(StringUtils.defaultIfBlank(instance.getBusinessType(), instance.getErrorMessage()));
        item.setCreatedAt(instance.getStartedAt());
        item.setDeadlineAt(instance.getDeadlineAt());
        item.setOverdue(instance.getDeadlineAt() != null && instance.getDeadlineAt() < System.currentTimeMillis());
        if (nodes != null) {
            item.setTotalNodeCount(nodes.size());
            item.setCompletedNodeCount((int) nodes.stream().filter(node -> "COMPLETED".equals(node.getStatus())
                    || "SKIPPED".equals(node.getStatus())).count());
        }
        return item;
    }

    /**
     * 审核Item。
     */
    private WorkbenchItemVo reviewItem(KnowledgeReviewTaskVo task) {
        WorkbenchItemVo item = new WorkbenchItemVo();
        item.setType("knowledge-review");
        item.setId(task.getId());
        item.setTitle(task.getDocumentTitle());
        item.setStatus(task.getStatus());
        item.setDescription(task.getSubmitComment());
        item.setCreatedAt(task.getSubmittedAt());
        return item;
    }

    /**
     * 处理deadLetterItem。
     */
    private WorkbenchItemVo deadLetterItem(AgentWorkflowDeadLetterVo letter) {
        WorkbenchItemVo item = new WorkbenchItemVo();
        item.setType("workflow-dead-letter");
        item.setId(letter.getId());
        item.setWorkflowId(letter.getInstanceId());
        item.setTitle(letter.getType());
        item.setStatus(letter.getStatus());
        item.setDescription(letter.getErrorMessage());
        item.setCreatedAt(letter.getOccurredAt());
        return item;
    }
}
