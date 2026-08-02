package com.aether.workflow.service.impl;

import com.alibaba.fastjson2.JSON;
import com.aether.workflow.dto.AgentWorkflowBusinessStartDto;
import com.aether.workflow.dto.AgentWorkflowScheduleTriggerDto;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowScheduleTrigger;
import com.aether.workflow.mapper.AgentWorkflowScheduleTriggerMapper;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.aether.workflow.service.AgentWorkflowScheduleTriggerService;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.exception.ServerException;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.service.ServiceAccountService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 定时触发器由数据库的 next_fire_at 和短租约协调，进程重启后可继续扫描。 */
@Service
public class AgentWorkflowScheduleTriggerServiceImpl extends ServiceImpl<AgentWorkflowScheduleTriggerMapper, AgentWorkflowScheduleTrigger>
        implements AgentWorkflowScheduleTriggerService {
    private static final long LEASE_MILLIS = 5 * 60 * 1000L;
    private final AgentWorkflowService workflowService;
    private final ServiceAccountService serviceAccountService;
    private final AgentWorkflowExecutionService executionService;

    public AgentWorkflowScheduleTriggerServiceImpl(AgentWorkflowService workflowService, ServiceAccountService serviceAccountService,
                                                    AgentWorkflowExecutionService executionService) {
        this.workflowService = workflowService;
        this.serviceAccountService = serviceAccountService;
        this.executionService = executionService;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowScheduleTrigger create(AgentWorkflowScheduleTriggerDto dto) {
        validate(dto);
        long now = System.currentTimeMillis();
        AgentWorkflowScheduleTrigger trigger = new AgentWorkflowScheduleTrigger();
        trigger.setWorkflowId(dto.getWorkflowId()); trigger.setServiceAccountId(dto.getServiceAccountId()); trigger.setName(dto.getName());
        trigger.setCronExpression(dto.getCronExpression()); trigger.setBusinessType(dto.getBusinessType()); trigger.setBusinessIdTemplate(dto.getBusinessIdTemplate());
        trigger.setVariables(JSON.toJSONString(dto.getVariables() == null ? new LinkedHashMap<String, Object>() : dto.getVariables()));
        trigger.setEnabled(true); trigger.setNextFireAt(nextFireAt(dto.getCronExpression(), now));
        save(trigger); return trigger;
    }

    @Override
    public boolean setEnabled(String id, boolean enabled) {
        AgentWorkflowScheduleTrigger trigger = required(id);
        trigger.setEnabled(enabled);
        if (enabled && (trigger.getNextFireAt() == null || trigger.getNextFireAt() < System.currentTimeMillis()))
            trigger.setNextFireAt(nextFireAt(trigger.getCronExpression(), System.currentTimeMillis()));
        return updateById(trigger);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean triggerDue(String id, long scheduledAt) {
        long now = System.currentTimeMillis();
        boolean claimed = update(new LambdaUpdateWrapper<AgentWorkflowScheduleTrigger>()
                .set(AgentWorkflowScheduleTrigger::getLockedUntil, now + LEASE_MILLIS)
                .eq(AgentWorkflowScheduleTrigger::getId, id).eq(AgentWorkflowScheduleTrigger::getEnabled, true)
                .eq(AgentWorkflowScheduleTrigger::getDeleted, false).le(AgentWorkflowScheduleTrigger::getNextFireAt, now)
                .and(w -> w.isNull(AgentWorkflowScheduleTrigger::getLockedUntil).or().le(AgentWorkflowScheduleTrigger::getLockedUntil, now)));
        if (!claimed) return false;
        AgentWorkflowScheduleTrigger trigger = required(id);
        try {
            ServiceAccount account = serviceAccountService.getById(trigger.getServiceAccountId());
            if (account == null || Boolean.TRUE.equals(account.getDeleted())) throw new ServerException(422, "定时触发器绑定的服务账号不存在");
            serviceAccountService.assertWorkflowStartAllowed(account.getId(), trigger.getWorkflowId());
            Map<String, Object> variables = StringUtils.isBlank(trigger.getVariables()) ? new LinkedHashMap<String, Object>()
                    : JSON.parseObject(trigger.getVariables(), Map.class);
            AgentWorkflowBusinessStartDto start = new AgentWorkflowBusinessStartDto();
            start.setBusinessType(trigger.getBusinessType()); start.setBusinessId(render(trigger.getBusinessIdTemplate(), trigger, scheduledAt));
            start.setIdempotencyKey("schedule:" + trigger.getId() + ":" + scheduledAt); start.setVariables(variables);
            executionService.startBusiness(trigger.getWorkflowId(), start, account.getUserId());
            update(new LambdaUpdateWrapper<AgentWorkflowScheduleTrigger>().set(AgentWorkflowScheduleTrigger::getLastTriggeredAt, now)
                    .set(AgentWorkflowScheduleTrigger::getLastErrorMessage, null).set(AgentWorkflowScheduleTrigger::getLockedUntil, null)
                    .set(AgentWorkflowScheduleTrigger::getNextFireAt, nextFireAt(trigger.getCronExpression(), now))
                    .eq(AgentWorkflowScheduleTrigger::getId, id));
            return true;
        } catch (RuntimeException ex) {
            update(new LambdaUpdateWrapper<AgentWorkflowScheduleTrigger>().set(AgentWorkflowScheduleTrigger::getLastErrorMessage,
                    StringUtils.abbreviate(ex.getMessage(), 2048)).set(AgentWorkflowScheduleTrigger::getLockedUntil, null).eq(AgentWorkflowScheduleTrigger::getId, id));
            return true;
        }
    }

    private void validate(AgentWorkflowScheduleTriggerDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getWorkflowId()) || StringUtils.isBlank(dto.getServiceAccountId()) || StringUtils.isBlank(dto.getName())
                || StringUtils.isBlank(dto.getCronExpression()) || StringUtils.isBlank(dto.getBusinessType()) || StringUtils.isBlank(dto.getBusinessIdTemplate()))
            throw new ServerException(422, "定时触发器必填配置不完整");
        try { CronExpression.parse(dto.getCronExpression()); } catch (Exception ex) { throw new ServerException(422, "Cron 表达式无效"); }
        AgentWorkflow workflow = workflowService.getById(dto.getWorkflowId());
        if (workflow == null || Boolean.TRUE.equals(workflow.getDeleted())) throw new ServerException(422, "定时触发器目标工作流不存在");
        if (serviceAccountService.getById(dto.getServiceAccountId()) == null) throw new ServerException(422, "定时触发器服务账号不存在");
    }
    private AgentWorkflowScheduleTrigger required(String id) {
        AgentWorkflowScheduleTrigger trigger = getById(id);
        if (trigger == null || Boolean.TRUE.equals(trigger.getDeleted())) throw new ServerException(404, "定时触发器不存在");
        return trigger;
    }
    private long nextFireAt(String cron, long after) {
        ZonedDateTime next = CronExpression.parse(cron).next(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(after), ZoneId.systemDefault()));
        if (next == null) throw new ServerException(422, "Cron 表达式无法计算下一次执行时间");
        return next.toInstant().toEpochMilli();
    }
    private String render(String source, AgentWorkflowScheduleTrigger trigger, long scheduledAt) {
        return StringUtils.defaultString(source).replace("${scheduledAt}", String.valueOf(scheduledAt)).replace("${triggerId}", trigger.getId());
    }
}
