package com.aether.workflow.runtime;

import com.alibaba.fastjson2.JSONObject;
import com.aether.workflow.entity.AgentWorkflowNodeInstance;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.aether.workflow.service.AgentWorkflowNodeInstanceService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** 扫描到期的延时节点并恢复对应实例。 */
@Component
public class WorkflowDelayScheduler {
    private final AgentWorkflowNodeInstanceService nodeService;
    private final AgentWorkflowExecutionService executionService;

    public WorkflowDelayScheduler(AgentWorkflowNodeInstanceService nodeService,
                                  @Lazy AgentWorkflowExecutionService executionService) {
        this.nodeService = nodeService;
        this.executionService = executionService;
    }

    @Scheduled(fixedDelayString = "${aether.workflow.delay.scan-interval-ms:1000}", initialDelay = 5000L)
    public void resumeDueDelays() {
        long now = System.currentTimeMillis();
        List<AgentWorkflowNodeInstance> nodes = nodeService.list(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class)
                .eq(AgentWorkflowNodeInstance::getStatus, "WAITING_DELAY")
                .eq(AgentWorkflowNodeInstance::getDeleted, false).last("LIMIT 100"));
        for (AgentWorkflowNodeInstance node : nodes) {
            try {
                JSONObject config = StringUtils.isBlank(node.getInteractionConfig()) ? null : JSONObject.parseObject(node.getInteractionConfig());
                if (config != null && config.getLongValue("resumeAt") <= now)
                    executionService.resumeDelay(node.getInstanceId(), node.getNodeId());
            } catch (RuntimeException ignored) {
                // 单个脏配置不得影响其余延时节点；实例详情保留原始配置供运维排查。
            }
        }
    }
}
