package com.aether.agent.application.service;

import com.aether.agent.application.entity.AgentApplication;
import com.aether.agent.application.service.AgentApplicationService;
import com.aether.exception.ServerException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.aether.local.CurrentUser;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** 在服务账号限额之上实施业务应用空间级总量限制。 */
@Service
public class ApplicationQuotaService {
    private final AgentApplicationService applicationService;
    private final RedisTemplate<String, Object> redisTemplate;
    public ApplicationQuotaService(AgentApplicationService applicationService, RedisTemplate<String, Object> redisTemplate) { this.applicationService = applicationService; this.redisTemplate = redisTemplate; }
    public void consumeAgentCall(String applicationId) { consume(applicationId, "agent", requireTenantApplication(applicationId).getMaxAgentCallsPerHour()); }
    public void consumeWorkflowStart(String applicationId) { consume(applicationId, "workflow", requireTenantApplication(applicationId).getMaxWorkflowStartsPerHour()); }
    private AgentApplication requireTenantApplication(String applicationId) {
        AgentApplication application = applicationService.requireActive(applicationId);
        String tenantId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
        if (tenantId != null && !tenantId.trim().isEmpty()
                && !tenantId.equals(application.getTenantId())) {
            throw new ServerException(404, "业务应用不存在");
        }
        return application;
    }
    private void consume(String appId, String resource, Integer configured) {
        int limit = configured == null ? 0 : configured; if (limit <= 0) return;
        String bucket = String.format(Locale.ROOT, "%1$tY%1$tm%1$td%1$tH", Calendar.getInstance());
        String tenantId = CurrentUser.getUser() == null ? "platform" : CurrentUser.getUser().get("tenantId");
        if (tenantId == null || tenantId.trim().isEmpty()) tenantId = "platform";
        String key = "AgentApplicationQuota:" + tenantId + ":" + appId + ":" + resource + ":" + bucket;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) redisTemplate.expire(key, 2, TimeUnit.HOURS);
        if (count != null && count > limit) throw new ServerException(429, "业务应用空间" + ("agent".equals(resource) ? " Agent 调用" : "工作流启动") + "配额已耗尽");
    }
}
