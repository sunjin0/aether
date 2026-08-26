package com.aether.sys.service.impl;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.application.service.AgentApplicationService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.sys.dto.ServiceAccountCreateDto;
import com.aether.sys.dto.ServiceAccountTokenDto;
import com.aether.sys.dto.ServiceAccountUpdateDto;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.mapper.ServiceAccountMapper;
import com.aether.sys.service.ServiceAccountService;
import com.aether.sys.vo.ServiceAccountSecretVo;
import com.aether.sys.vo.ServiceAccountTokenVo;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.utils.TokenUtils;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;

/**
 * 服务账号采用 client credentials，是独立于后台管理员的外部接入主体。
 */
@Service
public class ServiceAccountServiceImpl extends ServiceImpl<ServiceAccountMapper, ServiceAccount>
        implements ServiceAccountService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PRINCIPAL_TYPE_SERVICE_ACCOUNT = "SERVICE_ACCOUNT";
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AgentDefinitionService agentDefinitionService;
    private final int accessTokenSeconds;
    @Autowired(required = false)
    private AgentApplicationService applicationService;
    @Autowired(required = false)
    private AgentWorkflowService workflowService;

    /**
     * 创建 {@code ServiceAccountServiceImpl} 实例。
     */
    public ServiceAccountServiceImpl(PasswordEncoder passwordEncoder, RedisTemplate<String, Object> redisTemplate,
                                     AgentDefinitionService agentDefinitionService,
                                     @org.springframework.beans.factory.annotation.Value("${aether.service-account.access-token-seconds:900}") int accessTokenSeconds) {
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.agentDefinitionService = agentDefinitionService;
        this.accessTokenSeconds = Math.max(60, Math.min(accessTokenSeconds, 3600));
    }

    /**
     * 创建当前请求。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceAccountSecretVo create(ServiceAccountCreateDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getName()))
            throw new ServerException(422, I18nUtils.getMessage("service-account.name.required"));
        if (dto.getName().length() > 128)
            throw new ServerException(422, I18nUtils.getMessage("service-account.name.length.exceeded"));
        String clientId = StringUtils.defaultIfBlank(dto.getClientId(), "sa_" + randomToken(12));
        if (!clientId.matches("[A-Za-z][A-Za-z0-9_-]{2,63}"))
            throw new ServerException(422, I18nUtils.getMessage("service-account.client-id.invalid"));
        if (count(Wrappers.lambdaQuery(ServiceAccount.class).eq(ServiceAccount::getClientId, clientId).eq(ServiceAccount::getDeleted, false)) > 0)
            throw new ServerException(409, I18nUtils.getMessage("service-account.client-id.exists"));
        String secret = "sa_" + randomToken(32);
        ServiceAccount account = new ServiceAccount();
        String applicationId = normalizeApplicationId(dto.getApplicationId());
        account.setApplicationId(applicationId);
        account.setName(dto.getName());
        account.setDescription(StringUtils.abbreviate(dto.getDescription(), 1024));
        account.setClientId(clientId);
        account.setSecretHash(passwordEncoder.encode(secret));
        account.setTokenVersion(1);
        account.setEnabled(true);
        List<String> allowed = dto.getAllowedWorkflowIds() == null ? Collections.<String>emptyList() : dto.getAllowedWorkflowIds();
        account.setAllowedWorkflowIds(JSON.toJSONString(new ArrayList<String>(new LinkedHashSet<String>(allowed))));
        List<String> allowedAgents = dto.getAllowedAgentIds() == null ? Collections.<String>emptyList() : dto.getAllowedAgentIds();
        validateAgents(allowedAgents, applicationId);
        account.setAllowedAgentIds(JSON.toJSONString(new ArrayList<String>(new LinkedHashSet<String>(allowedAgents))));
        int maxStarts = dto.getMaxStartsPerHour() == null ? 0 : dto.getMaxStartsPerHour();
        if (maxStarts < 0 || maxStarts > 100000)
            throw new ServerException(422, I18nUtils.getMessage("service-account.hourly-start-limit.invalid"));
        account.setMaxStartsPerHour(maxStarts);
        int maxAgentCalls = dto.getMaxAgentCallsPerHour() == null ? 0 : dto.getMaxAgentCallsPerHour();
        validateAgentCallLimit(maxAgentCalls);
        account.setMaxAgentCallsPerHour(maxAgentCalls);
        save(account);
        return secretVo(account, secret);
    }

    /**
     * 更新当前请求。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(String id, ServiceAccountUpdateDto dto) {
        ServiceAccount account = required(id);
        if (dto == null || StringUtils.isBlank(dto.getName()))
            throw new ServerException(422, I18nUtils.getMessage("service-account.name.required"));
        if (dto.getName().length() > 128)
            throw new ServerException(422, I18nUtils.getMessage("service-account.name.length.exceeded"));
        int maxStarts = dto.getMaxStartsPerHour() == null ? 0 : dto.getMaxStartsPerHour();
        if (maxStarts < 0 || maxStarts > 100000)
            throw new ServerException(422, I18nUtils.getMessage("service-account.hourly-start-limit.invalid"));
        List<String> allowed = dto.getAllowedWorkflowIds() == null ? Collections.<String>emptyList() : dto.getAllowedWorkflowIds();
        List<String> allowedAgents = dto.getAllowedAgentIds() == null ? Collections.<String>emptyList() : dto.getAllowedAgentIds();
        String applicationId = normalizeApplicationId(StringUtils.defaultIfBlank(dto.getApplicationId(), account.getApplicationId()));
        validateAgents(allowedAgents, applicationId);
        account.setApplicationId(applicationId);
        int maxAgentCalls = dto.getMaxAgentCallsPerHour() == null ? 0 : dto.getMaxAgentCallsPerHour();
        validateAgentCallLimit(maxAgentCalls);
        account.setName(dto.getName());
        account.setDescription(StringUtils.abbreviate(dto.getDescription(), 1024));
        account.setAllowedWorkflowIds(JSON.toJSONString(new ArrayList<String>(new LinkedHashSet<String>(allowed))));
        account.setAllowedAgentIds(JSON.toJSONString(new ArrayList<String>(new LinkedHashSet<String>(allowedAgents))));
        account.setMaxStartsPerHour(maxStarts);
        account.setMaxAgentCallsPerHour(maxAgentCalls);
        boolean updated = updateById(account);
        if (updated) redisTemplate.opsForHash().delete(TokenUtils.TOKEN_KEY, principalId(account.getId()));
        return updated;
    }

    /**
     * 删除当前请求。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String id) {
        ServiceAccount account = required(id);
        redisTemplate.opsForHash().delete(TokenUtils.TOKEN_KEY, principalId(account.getId()));
        return baseMapper.physicalDeleteById(id) > 0;
    }

    /**
     * 处理rotateSecret。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceAccountSecretVo rotateSecret(String id) {
        ServiceAccount account = required(id);
        String secret = "sa_" + randomToken(32);
        account.setSecretHash(passwordEncoder.encode(secret));
        account.setTokenVersion((account.getTokenVersion() == null ? 0 : account.getTokenVersion()) + 1);
        updateById(account);
        return secretVo(account, secret);
    }

    /**
     * 处理issue令牌。
     */
    @Override
    public ServiceAccountTokenVo issueToken(ServiceAccountTokenDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getClientId()) || StringUtils.isBlank(dto.getClientSecret()))
            throw new ServerException(401, I18nUtils.getMessage("service-account.credentials.invalid"));
        ServiceAccount account = getOne(Wrappers.lambdaQuery(ServiceAccount.class).eq(ServiceAccount::getClientId, dto.getClientId())
                .eq(ServiceAccount::getDeleted, false));
        if (account == null || !Boolean.TRUE.equals(account.getEnabled()) || !passwordEncoder.matches(dto.getClientSecret(), account.getSecretHash()))
            throw new ServerException(401, I18nUtils.getMessage("service-account.credentials.invalid"));
        Map<String, String> claims = new HashMap<String, String>();
        claims.put("principalType", PRINCIPAL_TYPE_SERVICE_ACCOUNT);
        claims.put("principalId", principalId(account.getId()));
        claims.put("userId", principalId(account.getId()));
        claims.put("serviceAccountId", account.getId());
        claims.put("applicationId", normalizeApplicationId(account.getApplicationId()));
        claims.put("serviceTokenVersion", String.valueOf(account.getTokenVersion()));
        String accessToken = TokenUtils.createAccessToken(claims, accessTokenSeconds);
        account.setLastUsedAt(System.currentTimeMillis());
        updateById(account);
        ServiceAccountTokenVo result = new ServiceAccountTokenVo();
        result.setAccessToken(accessToken);
        result.setExpiresIn(accessTokenSeconds);
        return result;
    }

    /**
     * 处理setEnabled。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setEnabled(String id, boolean enabled) {
        ServiceAccount account = required(id);
        account.setEnabled(enabled);
        // 每次状态变更都推进版本；再次启用也不能恢复旧令牌。
        account.setTokenVersion((account.getTokenVersion() == null ? 0 : account.getTokenVersion()) + 1);
        boolean updated = updateById(account);
        if (updated) redisTemplate.opsForHash().delete(TokenUtils.TOKEN_KEY, principalId(account.getId()));
        return updated;
    }

    /**
     * 判断是否为Active。
     */
    @Override
    public boolean isActive(String serviceAccountId, String tokenVersion) {
        if (StringUtils.isBlank(serviceAccountId) || StringUtils.isBlank(tokenVersion)) return false;
        ServiceAccount account = getById(serviceAccountId);
        return account != null && !Boolean.TRUE.equals(account.getDeleted()) && Boolean.TRUE.equals(account.getEnabled())
                && StringUtils.equals(String.valueOf(account.getTokenVersion()), tokenVersion);
    }

    /**
     * 处理assert工作流StartAllowed。
     */
    @Override
    public void assertWorkflowStartAllowed(String id, String workflowId) {
        ServiceAccount account = required(id);
        if (!Boolean.TRUE.equals(account.getEnabled()))
            throw new ServerException(403, I18nUtils.getMessage("service-account.disabled"));
        List<String> allowed = StringUtils.isBlank(account.getAllowedWorkflowIds()) ? Collections.<String>emptyList()
                : JSON.parseArray(account.getAllowedWorkflowIds(), String.class);
        if (allowed != null && !allowed.isEmpty() && !allowed.contains(workflowId))
            throw new ServerException(403, I18nUtils.getMessage("service-account.workflow-start.denied"));
        if (workflowService != null) {
            AgentWorkflow workflow = workflowService.getById(workflowId);
            if (workflow == null || Boolean.TRUE.equals(workflow.getDeleted()))
                throw new ServerException(404, "工作流不存在");
            if (!StringUtils.equals(normalizeApplicationId(account.getApplicationId()), normalizeApplicationId(workflow.getApplicationId())))
                throw new ServerException(403, I18nUtils.getMessage("service-account.workflow-start.denied"));
        }
        int limit = account.getMaxStartsPerHour() == null ? 0 : account.getMaxStartsPerHour();
        if (limit <= 0) return;
        Calendar calendar = Calendar.getInstance();
        String bucket = String.format(Locale.ROOT, "%1$tY%1$tm%1$td%1$tH", calendar);
        String key = "ServiceAccountWorkflowStarts:" + account.getId() + ":" + bucket;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) redisTemplate.expire(key, 2, java.util.concurrent.TimeUnit.HOURS);
        if (count != null && count > limit)
            throw new ServerException(429, I18nUtils.getMessage("service-account.workflow-start-quota.exhausted"));
    }

    /**
     * 处理assert Agent CallAllowed。
     */
    @Override
    public void assertAgentCallAllowed(String id, String agentId) {
        ServiceAccount account = required(id);
        if (!Boolean.TRUE.equals(account.getEnabled()))
            throw new ServerException(403, I18nUtils.getMessage("service-account.disabled"));
        List<String> allowed = StringUtils.isBlank(account.getAllowedAgentIds()) ? Collections.<String>emptyList()
                : JSON.parseArray(account.getAllowedAgentIds(), String.class);
        if (allowed == null || allowed.isEmpty() || !allowed.contains(agentId))
            throw new ServerException(403, I18nUtils.getMessage("service-account.agent-call.denied"));
        AgentDefinition agent = agentDefinitionService.getById(agentId);
        if (agent == null || Boolean.TRUE.equals(agent.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("agent.definition.not.found"));
        if (!Integer.valueOf(1).equals(agent.getStatus()))
            throw new ServerException(422, I18nUtils.getMessage("agent.definition.disabled"));
        if (!StringUtils.equals(normalizeApplicationId(account.getApplicationId()), normalizeApplicationId(agent.getApplicationId())))
            throw new ServerException(403, I18nUtils.getMessage("service-account.agent-call.denied"));
        int limit = account.getMaxAgentCallsPerHour() == null ? 0 : account.getMaxAgentCallsPerHour();
        if (limit <= 0) return;
        Calendar calendar = Calendar.getInstance();
        String bucket = String.format(Locale.ROOT, "%1$tY%1$tm%1$td%1$tH", calendar);
        String key = "ServiceAccountAgentCalls:" + account.getId() + ":" + bucket;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) redisTemplate.expire(key, 2, java.util.concurrent.TimeUnit.HOURS);
        if (count != null && count > limit)
            throw new ServerException(429, I18nUtils.getMessage("service-account.agent-call-quota.exhausted"));
    }

    /**
     * 处理required。
     */
    private ServiceAccount required(String id) {
        ServiceAccount account = getById(id);
        if (account == null || Boolean.TRUE.equals(account.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("service-account.not-found"));
        return account;
    }

    /**
     * 处理secretVO。
     */
    private ServiceAccountSecretVo secretVo(ServiceAccount account, String secret) {
        ServiceAccountSecretVo result = new ServiceAccountSecretVo();
        result.setId(account.getId());
        result.setName(account.getName());
        result.setClientId(account.getClientId());
        result.setClientSecret(secret);
        return result;
    }

    private void validateAgents(List<String> agentIds, String applicationId) {
        if (agentIds == null || agentIds.isEmpty()) return;
        Set<String> unique = new LinkedHashSet<String>(agentIds);
        long found = agentDefinitionService.count(Wrappers.lambdaQuery(AgentDefinition.class)
                .in(AgentDefinition::getId, unique).eq(AgentDefinition::getApplicationId, applicationId)
                .eq(AgentDefinition::getDeleted, false));
        if (found != unique.size())
            throw new ServerException(422, I18nUtils.getMessage("service-account.agents.invalid"));
    }

    private void validateAgentCallLimit(int value) {
        if (value < 0 || value > 100000)
            throw new ServerException(422, I18nUtils.getMessage("service-account.hourly-agent-call-limit.invalid"));
    }

    private String normalizeApplicationId(String applicationId) {
        String value = StringUtils.defaultIfBlank(applicationId, AgentApplicationService.PLATFORM_APPLICATION_ID);
        if (applicationService != null) applicationService.requireActive(value);
        return value;
    }

    public static String principalId(String serviceAccountId) {
        return "sa:" + serviceAccountId;
    }

    /**
     * 处理random令牌。
     */
    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
