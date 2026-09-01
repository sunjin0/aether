package com.aether.workflow.service.impl;

import com.alibaba.fastjson2.JSON;
import com.aether.workflow.dto.AgentWorkflowBusinessStartDto;
import com.aether.workflow.dto.AgentWorkflowScheduleTriggerDto;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowScheduleTrigger;
import com.aether.workflow.mapper.AgentWorkflowScheduleTriggerMapper;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.aether.workflow.service.AgentWorkflowScheduleTriggerService;
import com.aether.local.CurrentUser;
import org.apache.commons.lang3.StringUtils;
import com.aether.local.CurrentUser;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.service.ServiceAccountService;
import com.aether.sys.service.impl.ServiceAccountServiceImpl;
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

/**
 * 定时触发器由数据库的 next_fire_at 和短租约协调，进程重启后可继续扫描。
 */
@Service
public class AgentWorkflowScheduleTriggerServiceImpl extends ServiceImpl<AgentWorkflowScheduleTriggerMapper, AgentWorkflowScheduleTrigger>
        implements AgentWorkflowScheduleTriggerService {
    @Override
    public AgentWorkflowScheduleTrigger getById(java.io.Serializable id) {
        AgentWorkflowScheduleTrigger trigger = super.getById(id);
        String tenantId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
        if (trigger != null && StringUtils.isNotBlank(tenantId) && !tenantId.equals(trigger.getTenantId())) return null;
        return trigger;
    }
    private static final long LEASE_MILLIS = 5 * 60 * 1000L;
    private final AgentWorkflowService workflowService;
    private final ServiceAccountService serviceAccountService;
    private final AgentWorkflowExecutionService executionService;

    /**
     * 创建 {@code AgentWorkflowScheduleTriggerServiceImpl} 实例。
     */
    public AgentWorkflowScheduleTriggerServiceImpl(AgentWorkflowService workflowService, ServiceAccountService serviceAccountService,
                                                   AgentWorkflowExecutionService executionService) {
        this.workflowService = workflowService;
        this.serviceAccountService = serviceAccountService;
        this.executionService = executionService;
    }

    /**
     * 创建当前请求。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowScheduleTrigger create(AgentWorkflowScheduleTriggerDto dto) {
        validate(dto);
        long now = System.currentTimeMillis();
        AgentWorkflowScheduleTrigger trigger = new AgentWorkflowScheduleTrigger();
        trigger.setTenantId(CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId"));
        trigger.setWorkflowId(dto.getWorkflowId());
        trigger.setServiceAccountId(dto.getServiceAccountId());
        trigger.setName(dto.getName());
        trigger.setCronExpression(dto.getCronExpression());
        trigger.setBusinessType(dto.getBusinessType());
        trigger.setBusinessIdTemplate(dto.getBusinessIdTemplate());
        trigger.setVariables(JSON.toJSONString(dto.getVariables() == null ? new LinkedHashMap<String, Object>() : dto.getVariables()));
        trigger.setEnabled(true);
        trigger.setNextFireAt(nextFireAt(dto.getCronExpression(), now));
        save(trigger);
        return trigger;
    }

    /**
     * 更新当前请求。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(String id, AgentWorkflowScheduleTriggerDto dto) {
        validate(dto);
        AgentWorkflowScheduleTrigger trigger = required(id);
        trigger.setWorkflowId(dto.getWorkflowId());
        trigger.setServiceAccountId(dto.getServiceAccountId());
        trigger.setName(dto.getName());
        trigger.setCronExpression(dto.getCronExpression());
        trigger.setBusinessType(dto.getBusinessType());
        trigger.setBusinessIdTemplate(dto.getBusinessIdTemplate());
        trigger.setVariables(JSON.toJSONString(dto.getVariables() == null ? new LinkedHashMap<String, Object>() : dto.getVariables()));
        trigger.setLockedUntil(null);
        trigger.setLastErrorMessage(null);
        trigger.setNextFireAt(nextFireAt(dto.getCronExpression(), System.currentTimeMillis()));
        return updateById(trigger);
    }

    /**
     * 处理setEnabled。
     */
    @Override
    public boolean setEnabled(String id, boolean enabled) {
        AgentWorkflowScheduleTrigger trigger = required(id);
        trigger.setEnabled(enabled);
        trigger.setLockedUntil(null);
        if (enabled && (trigger.getNextFireAt() == null || trigger.getNextFireAt() < System.currentTimeMillis()))
            trigger.setNextFireAt(nextFireAt(trigger.getCronExpression(), System.currentTimeMillis()));
        return updateById(trigger);
    }

    /**
     * 删除当前请求。
     */
    @Override
    public boolean delete(String id) {
        AgentWorkflowScheduleTrigger trigger = required(id);
        trigger.setEnabled(false);
        trigger.setLockedUntil(null);
        return removeById(trigger);
    }

    /**
     * 处理triggerDue。
     */
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
            if (account == null || Boolean.TRUE.equals(account.getDeleted()))
                throw new ServerException(422, I18nUtils.getMessage("workflow.schedule-trigger.service-account-binding.not-found"));
            serviceAccountService.assertWorkflowStartAllowed(account.getId(), trigger.getWorkflowId());
            Map<String, Object> variables = StringUtils.isBlank(trigger.getVariables()) ? new LinkedHashMap<String, Object>()
                    : JSON.parseObject(trigger.getVariables(), Map.class);
            AgentWorkflowBusinessStartDto start = new AgentWorkflowBusinessStartDto();
            start.setBusinessType(trigger.getBusinessType());
            start.setBusinessId(render(trigger.getBusinessIdTemplate(), trigger, scheduledAt));
            start.setIdempotencyKey("schedule:" + trigger.getId() + ":" + scheduledAt);
            start.setVariables(variables);
            executionService.startBusiness(trigger.getWorkflowId(), start,
                    ServiceAccountServiceImpl.principalId(account.getId()));
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

    /**
     * 校验当前请求。
     */
    private void validate(AgentWorkflowScheduleTriggerDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getWorkflowId()) || StringUtils.isBlank(dto.getServiceAccountId()) || StringUtils.isBlank(dto.getName())
                || StringUtils.isBlank(dto.getCronExpression()) || StringUtils.isBlank(dto.getBusinessType()) || StringUtils.isBlank(dto.getBusinessIdTemplate()))
            throw new ServerException(422, I18nUtils.getMessage("workflow.schedule-trigger.configuration.required"));
        try {
            CronExpression.parse(dto.getCronExpression());
        } catch (Exception ex) {
            throw new ServerException(422, I18nUtils.getMessage("workflow.schedule-trigger.cron.invalid"));
        }
        AgentWorkflow workflow = workflowService.getById(dto.getWorkflowId());
        if (workflow == null || Boolean.TRUE.equals(workflow.getDeleted()))
            throw new ServerException(422, I18nUtils.getMessage("workflow.schedule-trigger.workflow.not-found"));
        if (serviceAccountService.getById(dto.getServiceAccountId()) == null)
            throw new ServerException(422, I18nUtils.getMessage("workflow.schedule-trigger.service-account.not-found"));
    }

    /**
     * 处理required。
     */
    private AgentWorkflowScheduleTrigger required(String id) {
        AgentWorkflowScheduleTrigger trigger = getById(id);
        if (trigger == null || Boolean.TRUE.equals(trigger.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("workflow.schedule-trigger.not-found"));
        return trigger;
    }

    /**
     * 下一个FireAt。
     */
    private long nextFireAt(String cron, long after) {
        ZonedDateTime next = CronExpression.parse(cron).next(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(after), ZoneId.systemDefault()));
        if (next == null)
            throw new ServerException(422, I18nUtils.getMessage("workflow.schedule-trigger.cron.next-execution.unavailable"));
        return next.toInstant().toEpochMilli();
    }

    /**
     * 处理render。
     */
    private String render(String source, AgentWorkflowScheduleTrigger trigger, long scheduledAt) {
        return StringUtils.defaultString(source).replace("${scheduledAt}", String.valueOf(scheduledAt)).replace("${triggerId}", trigger.getId());
    }
}
