package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentSessionMemory;
import com.aether.agent.mapper.AgentSessionMemoryMapper;
import com.aether.agent.service.AgentDerivedContextInvalidationService;
import com.aether.agent.service.AgentSessionService;
import com.aether.agent.service.AgentSessionMemoryService;
import com.aether.exception.ServerException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 实现智能体会话Memory业务服务。
 */
@Service
public class AgentSessionMemoryServiceImpl extends ServiceImpl<AgentSessionMemoryMapper, AgentSessionMemory>
        implements AgentSessionMemoryService {
    private static final int CONTENT_LIMIT = 2000;
    private static final String[] FORBIDDEN_MEMORY_PATTERNS = {
            "(?i)password\\s*[:=]",
            "(?i)passwd\\s*[:=]",
            "(?i)secret\\s*[:=]",
            "(?i)api[_-]?key\\s*[:=]",
            "(?i)access[_-]?token\\s*[:=]",
            "(?i)private[_-]?key\\s*[:=]"
    };
    private final AgentSessionService sessionService;
    private final AgentDerivedContextInvalidationService invalidationService;

    /**
     * 创建 {@code AgentSessionMemoryServiceImpl} 实例。
     */
    public AgentSessionMemoryServiceImpl(AgentSessionService sessionService) {
        this(sessionService, null);
    }

    /**
     * 创建 {@code AgentSessionMemoryServiceImpl} 实例。
     */
    @Autowired
    public AgentSessionMemoryServiceImpl(AgentSessionService sessionService,
                                         AgentDerivedContextInvalidationService invalidationService) {
        this.sessionService = sessionService;
        this.invalidationService = invalidationService;
    }

    /**
     * 处理record任务Conclusion。
     */
    @Override
    public void recordTaskConclusion(String sessionId, String taskId, String runId, String content) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(content)) return;
        AgentSessionMemory memory = new AgentSessionMemory();
        String sanitized = sanitize(content);
        memory.setSessionId(sessionId);
        memory.setMemoryType("TASK_CONCLUSION");
        memory.setContent(sanitized);
        memory.setSummary(StringUtils.abbreviate(sanitized, 500));
        memory.setSourceTaskId(taskId);
        memory.setSourceRunId(runId);
        memory.setImportance(80);
        memory.setConfidence(80);
        memory.setStatus(AgentSessionMemory.STATUS_ACTIVE);
        memory.setSensitivityLevel("NORMAL");
        memory.setMemoryVersion(1);
        save(memory);
        bumpSessionMemoryVersion(sessionId);
        invalidateDerivedContext(sessionId);
    }

    /**
     * 记录经校验的自动提取记忆。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentSessionMemory recordExtractedMemory(String sessionId, String memoryType, String content,
                                                    String sourceMessageId, Integer confidence,
                                                    String sensitivityLevel) {
        return recordExtractedMemory(sessionId, memoryType, content, sourceMessageId, confidence,
                sensitivityLevel, null, null, null);
    }

    /**
     * 记录经校验的自动提取记忆，并保存提取来源元数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentSessionMemory recordExtractedMemory(String sessionId, String memoryType, String content,
                                                    String sourceMessageId, Integer confidence,
                                                    String sensitivityLevel, String extractorVersion,
                                                    String candidateHash, String sourceEventRange) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(content)) {
            throw new ServerException(400, "记忆参数不完整");
        }
        String type = StringUtils.upperCase(StringUtils.trimToEmpty(memoryType));
        if (!isAutoExtractableType(type)) {
            throw new ServerException(400, "不支持自动写入该记忆类型");
        }
        if (containsForbiddenMemoryContent(content)) {
            throw new ServerException(400, "记忆内容包含禁止保存的敏感信息");
        }
        String sanitized = sanitize(content);
        if (StringUtils.isBlank(sanitized)) {
            throw new ServerException(400, "记忆内容不能为空");
        }
        AgentSessionMemory memory = new AgentSessionMemory();
        memory.setSessionId(sessionId);
        memory.setMemoryType(type);
        memory.setContent(sanitized);
        memory.setSummary(StringUtils.abbreviate(sanitized, 500));
        memory.setSourceMessageId(sourceMessageId);
        memory.setSourceEventRange(StringUtils.abbreviate(sourceEventRange, 256));
        memory.setExtractorVersion(StringUtils.abbreviate(extractorVersion, 64));
        memory.setCandidateHash(StringUtils.abbreviate(candidateHash, 64));
        memory.setImportance(defaultImportance(type));
        memory.setConfidence(confidence == null ? 70 : Math.max(0, Math.min(100, confidence)));
        memory.setStatus(AgentSessionMemory.STATUS_ACTIVE);
        memory.setSensitivityLevel(StringUtils.defaultIfBlank(sensitivityLevel, "NORMAL"));
        memory.setMemoryVersion(1);
        save(memory);
        bumpSessionMemoryVersion(sessionId);
        invalidateDerivedContext(sessionId);
        return memory;
    }

    /**
     * 查询Injectable。
     */
    @Override
    public List<AgentSessionMemory> listInjectable(String sessionId, int limit) {
        if (StringUtils.isBlank(sessionId) || limit <= 0) return java.util.Collections.emptyList();
        long now = System.currentTimeMillis();
        return list(Wrappers.lambdaQuery(AgentSessionMemory.class)
                .eq(AgentSessionMemory::getSessionId, sessionId)
                .eq(AgentSessionMemory::getDeleted, false)
                .and(query -> query.isNull(AgentSessionMemory::getExpiresAt).or()
                        .gt(AgentSessionMemory::getExpiresAt, now))
                .orderByDesc(AgentSessionMemory::getImportance)
                .orderByDesc(AgentSessionMemory::getCreatedAt)
                .last("limit " + Math.min(limit, 12)));
    }

    /**
     * 用户修正记忆：创建新 ACTIVE 记录，并将旧记录标记为 SUPERSEDED。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentSessionMemory correctMemory(String sessionId, String memoryId, String content,
                                            String reason, Integer expectedMemoryVersion) {
        AgentSessionMemory current = requireMutableMemory(sessionId, memoryId, expectedMemoryVersion);
        String sanitized = sanitize(content);
        if (StringUtils.isBlank(sanitized)) {
            throw new ServerException(400, "记忆内容不能为空");
        }
        if (StringUtils.isBlank(reason)) {
            throw new ServerException(400, "修正原因不能为空");
        }

        AgentSessionMemory replacement = new AgentSessionMemory();
        replacement.setSessionId(sessionId);
        replacement.setMemoryType(current.getMemoryType());
        replacement.setContent(sanitized);
        replacement.setSummary(StringUtils.abbreviate(sanitized, 500));
        replacement.setSourceMessageId(current.getSourceMessageId());
        replacement.setSourceTaskId(current.getSourceTaskId());
        replacement.setSourceRunId(current.getSourceRunId());
        replacement.setImportance(current.getImportance());
        replacement.setConfidence(100);
        replacement.setStatus(AgentSessionMemory.STATUS_ACTIVE);
        replacement.setSensitivityLevel(StringUtils.defaultIfBlank(current.getSensitivityLevel(), "NORMAL"));
        replacement.setCorrectionReason(StringUtils.abbreviate(reason, 500));
        replacement.setExpiresAt(current.getExpiresAt());
        replacement.setMemoryVersion(nextMemoryVersion(current));
        save(replacement);

        boolean updated = update(Wrappers.<AgentSessionMemory>lambdaUpdate()
                .eq(AgentSessionMemory::getId, memoryId)
                .eq(AgentSessionMemory::getSessionId, sessionId)
                .eq(AgentSessionMemory::getDeleted, false)
                .eq(AgentSessionMemory::getMemoryVersion, current.getMemoryVersion())
                .set(AgentSessionMemory::getStatus, AgentSessionMemory.STATUS_SUPERSEDED)
                .set(AgentSessionMemory::getSupersededById, replacement.getId())
                .set(AgentSessionMemory::getCorrectionReason, StringUtils.abbreviate(reason, 500)));
        if (!updated) {
            throw new ServerException(409, "记忆版本已变化，请刷新后重试");
        }
        bumpSessionMemoryVersion(sessionId);
        invalidateDerivedContext(sessionId);
        return replacement;
    }

    /**
     * 用户删除记忆：从未来上下文移除。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMemory(String sessionId, String memoryId, Integer expectedMemoryVersion, String reason) {
        AgentSessionMemory current = requireMutableMemory(sessionId, memoryId, expectedMemoryVersion);
        boolean updated = update(Wrappers.<AgentSessionMemory>lambdaUpdate()
                .eq(AgentSessionMemory::getId, memoryId)
                .eq(AgentSessionMemory::getSessionId, sessionId)
                .eq(AgentSessionMemory::getDeleted, false)
                .eq(AgentSessionMemory::getMemoryVersion, current.getMemoryVersion())
                .set(AgentSessionMemory::getStatus, AgentSessionMemory.STATUS_DELETED)
                .set(AgentSessionMemory::getCorrectionReason, StringUtils.abbreviate(StringUtils.defaultString(reason), 500))
                .set(AgentSessionMemory::getDeleted, true));
        if (!updated) {
            throw new ServerException(409, "记忆版本已变化，请刷新后重试");
        }
        bumpSessionMemoryVersion(sessionId);
        invalidateDerivedContext(sessionId);
    }

    /**
     * 用户反馈记忆状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentSessionMemory feedback(String sessionId, String memoryId, Integer expectedMemoryVersion,
                                       String verdict, String reason) {
        String normalized = StringUtils.upperCase(StringUtils.trimToEmpty(verdict));
        if (!"ACCURATE".equals(normalized) && !"INACCURATE".equals(normalized) && !"EXPIRED".equals(normalized)) {
            throw new ServerException(400, "不支持的记忆反馈");
        }
        if ("INACCURATE".equals(normalized) && StringUtils.isBlank(reason)) {
            throw new ServerException(400, "不准确反馈必须填写原因");
        }
        AgentSessionMemory current = requireMutableMemory(sessionId, memoryId, expectedMemoryVersion);
        if ("ACCURATE".equals(normalized)) {
            AgentSessionMemory update = new AgentSessionMemory();
            update.setId(memoryId);
            update.setConfidence(100);
            update.setMemoryVersion(nextMemoryVersion(current));
            updateById(update);
            AgentSessionMemory refreshed = getById(memoryId);
            bumpSessionMemoryVersion(sessionId);
            invalidateDerivedContext(sessionId);
            return refreshed;
        }
        if ("EXPIRED".equals(normalized)) {
            AgentSessionMemory update = new AgentSessionMemory();
            update.setId(memoryId);
            update.setExpiresAt(System.currentTimeMillis());
            update.setStatus(AgentSessionMemory.STATUS_DELETED);
            update.setDeleted(true);
            update.setCorrectionReason(StringUtils.abbreviate(StringUtils.defaultString(reason), 500));
            update.setMemoryVersion(nextMemoryVersion(current));
            updateById(update);
            bumpSessionMemoryVersion(sessionId);
            invalidateDerivedContext(sessionId);
            return update;
        }
        deleteMemory(sessionId, memoryId, expectedMemoryVersion, reason);
        AgentSessionMemory removed = new AgentSessionMemory();
        removed.setId(memoryId);
        removed.setStatus(AgentSessionMemory.STATUS_DELETED);
        return removed;
    }

    /**
     * 以治理规则过滤的活跃记忆：仅 {@code ACTIVE} 且未过期、非敏感受限的记忆进入模型输入。
     */
    @Override
    public List<AgentSessionMemory> listInjectableForModel(String sessionId, int limit) {
        if (StringUtils.isBlank(sessionId) || limit <= 0) return java.util.Collections.emptyList();
        long now = System.currentTimeMillis();
        return list(Wrappers.lambdaQuery(AgentSessionMemory.class)
                .eq(AgentSessionMemory::getSessionId, sessionId)
                .eq(AgentSessionMemory::getDeleted, false)
                .and(query -> query.isNull(AgentSessionMemory::getStatus)
                        .or().eq(AgentSessionMemory::getStatus, AgentSessionMemory.STATUS_ACTIVE))
                .and(query -> query.isNull(AgentSessionMemory::getExpiresAt).or()
                        .gt(AgentSessionMemory::getExpiresAt, now))
                .ne(AgentSessionMemory::getSensitivityLevel, "RESTRICTED")
                .orderByDesc(AgentSessionMemory::getImportance)
                .orderByDesc(AgentSessionMemory::getCreatedAt)
                .last("limit " + Math.min(limit, 12)));
    }

    /**
     * 处理expireDueMemories。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireDueMemories() {
        long now = System.currentTimeMillis();
        List<AgentSessionMemory> dueMemories = list(Wrappers.lambdaQuery(AgentSessionMemory.class)
                .select(AgentSessionMemory::getSessionId)
                .eq(AgentSessionMemory::getDeleted, false)
                .isNotNull(AgentSessionMemory::getExpiresAt)
                .le(AgentSessionMemory::getExpiresAt, now));
        if (dueMemories.isEmpty()) {
            return 0;
        }
        AgentSessionMemory update = new AgentSessionMemory();
        update.setDeleted(true);
        int updated = baseMapper.update(update, Wrappers.lambdaUpdate(AgentSessionMemory.class)
                .eq(AgentSessionMemory::getDeleted, false)
                .isNotNull(AgentSessionMemory::getExpiresAt)
                .le(AgentSessionMemory::getExpiresAt, now));
        if (updated > 0) {
            Set<String> affectedSessionIds = dueMemories.stream()
                    .map(AgentSessionMemory::getSessionId)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet());
            for (String sessionId : affectedSessionIds) {
                bumpSessionMemoryVersion(sessionId);
                invalidateDerivedContext(sessionId);
            }
        }
        return updated;
    }

    /**
     * 清理敏感信息当前请求。
     */
    private String sanitize(String value) {
        String compact = value.replaceAll("(?i)(password|passwd|secret|api[_-]?key|access[_-]?token|private[_-]?key)\\s*[:=]\\s*\\S+", "$1=[REDACTED]");
        return StringUtils.abbreviate(compact, CONTENT_LIMIT);
    }

    /**
     * 自动提取允许的记忆类型。
     */
    private boolean isAutoExtractableType(String type) {
        return "GOAL".equals(type) || "CONSTRAINT".equals(type) || "FACT".equals(type)
                || "DECISION".equals(type) || "TODO".equals(type) || "ARTIFACT".equals(type);
    }

    /**
     * 检测禁止持久化的敏感内容。
     */
    private boolean containsForbiddenMemoryContent(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        for (String pattern : FORBIDDEN_MEMORY_PATTERNS) {
            if (value.matches("(?s).*" + pattern + ".*")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 默认重要度，采用文档中的 1-5 范围。
     */
    private int defaultImportance(String type) {
        if ("GOAL".equals(type) || "CONSTRAINT".equals(type) || "DECISION".equals(type)) {
            return 5;
        }
        if ("TODO".equals(type)) {
            return 4;
        }
        return 3;
    }

    /**
     * 查询并校验可变更记忆。
     */
    private AgentSessionMemory requireMutableMemory(String sessionId, String memoryId, Integer expectedMemoryVersion) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(memoryId)) {
            throw new ServerException(400, "记忆参数不完整");
        }
        AgentSessionMemory memory = getById(memoryId);
        if (memory == null || Boolean.TRUE.equals(memory.getDeleted()) || !sessionId.equals(memory.getSessionId())) {
            throw new ServerException(404, "记忆不存在");
        }
        if (!AgentSessionMemory.STATUS_ACTIVE.equals(StringUtils.defaultIfBlank(memory.getStatus(), AgentSessionMemory.STATUS_ACTIVE))) {
            throw new ServerException(409, "记忆已不可修改");
        }
        if (expectedMemoryVersion != null && memory.getMemoryVersion() != null
                && !expectedMemoryVersion.equals(memory.getMemoryVersion())) {
            throw new ServerException(409, "记忆版本已变化，请刷新后重试");
        }
        return memory;
    }

    /**
     * 计算下一条记忆版本。
     */
    private int nextMemoryVersion(AgentSessionMemory memory) {
        return memory.getMemoryVersion() == null ? 1 : memory.getMemoryVersion() + 1;
    }

    /**
     * 递增会话记忆版本，用于使派生上下文失效。
     */
    private void bumpSessionMemoryVersion(String sessionId) {
        if (sessionService == null || StringUtils.isBlank(sessionId)) {
            return;
        }
        sessionService.update(Wrappers.<com.aether.agent.entity.AgentSession>lambdaUpdate()
                .eq(com.aether.agent.entity.AgentSession::getId, sessionId)
                .setSql("memory_version = COALESCE(memory_version, 0) + 1")
                .set(com.aether.agent.entity.AgentSession::getLastActiveAt, System.currentTimeMillis()));
    }

    /**
     * 让依赖记忆的摘要、缓存和偏好索引在事务提交后失效。
     */
    private void invalidateDerivedContext(String sessionId) {
        if (invalidationService == null || StringUtils.isBlank(sessionId)) {
            return;
        }
        invalidationService.invalidateSession(sessionId);
    }
}
