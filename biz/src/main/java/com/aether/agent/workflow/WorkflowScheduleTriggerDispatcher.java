package com.aether.agent.workflow;

import com.aether.agent.entity.AgentWorkflowScheduleTrigger;
import com.aether.agent.service.AgentWorkflowScheduleTriggerService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

/** 扫描到期调度定义；实际领取由服务层的条件更新完成，以支持多实例部署。 */
@Component
public class WorkflowScheduleTriggerDispatcher {
    private final AgentWorkflowScheduleTriggerService triggerService;
    public WorkflowScheduleTriggerDispatcher(AgentWorkflowScheduleTriggerService triggerService) { this.triggerService = triggerService; }
    @Scheduled(fixedDelayString = "${aether.workflow.schedule.scan-interval-ms:5000}", initialDelay = 10000L)
    public void triggerDueSchedules() {
        long now = System.currentTimeMillis();
        List<AgentWorkflowScheduleTrigger> triggers = triggerService.list(Wrappers.lambdaQuery(AgentWorkflowScheduleTrigger.class)
                .eq(AgentWorkflowScheduleTrigger::getEnabled, true).eq(AgentWorkflowScheduleTrigger::getDeleted, false)
                .le(AgentWorkflowScheduleTrigger::getNextFireAt, now).orderByAsc(AgentWorkflowScheduleTrigger::getNextFireAt).last("LIMIT 20"));
        for (AgentWorkflowScheduleTrigger trigger : triggers) triggerService.triggerDue(trigger.getId(), trigger.getNextFireAt());
    }
}
