package com.aether.agent.service;

import com.aether.agent.entity.ModelProvider;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Classifies and sanitizes conversation-summary prompts before they are sent to a compression model.
 */
@Service
public class CompressionOutboundGovernanceService {
    private static final Logger log = LoggerFactory.getLogger(CompressionOutboundGovernanceService.class);
    private static final Pattern HIGH_SECRET = Pattern.compile(
            "(?is)(-----BEGIN [A-Z ]*PRIVATE KEY-----|\\\"?(api[_-]?key|access[_-]?token|refresh[_-]?token|secret|password)\\\"?\\s*[:=]\\s*[\\\"']?[^\\s,;}{\\\"]{8,})");
    private static final Pattern CONTACT = Pattern.compile(
            "(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}|(?<![0-9])1[3-9][0-9]{9}(?![0-9])");
    private static final Pattern CN_ID = Pattern.compile(
            "(?<![0-9Xx])[1-9][0-9]{5}(?:18|19|20)?[0-9]{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12][0-9]|3[01])[0-9]{3}[0-9Xx](?![0-9Xx])");
    private static final Pattern BANK_CARD = Pattern.compile("(?<![0-9])[0-9]{16,19}(?![0-9])");

    private final boolean enabled;

    /**
     * 创建 {@code CompressionOutboundGovernanceService} 实例。
     */
    public CompressionOutboundGovernanceService(
            @Value("${aether.agent.compression.outbound-governance.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 对压缩出站提示词执行本地分类和脱敏；阻断时不返回原文。
     */
    public Decision review(String conversationId, String prompt, ModelProvider provider) {
        if (!enabled || StringUtils.isBlank(prompt)) {
            return Decision.allowed(prompt, false, "DISABLED_OR_EMPTY");
        }
        String providerType = provider == null ? "unknown" : StringUtils.defaultString(provider.getType(), "unknown");
        if (provider != null && Boolean.FALSE.equals(provider.getCompressionOutboundAllowed())) {
            log.warn("Compression outbound blocked: conversationId={}, providerType={}, reason=PROVIDER_NOT_ALLOWED",
                    conversationId, providerType);
            return Decision.blocked("PROVIDER_NOT_ALLOWED");
        }
        Decision policyDecision = reviewProviderPolicy(conversationId, provider, providerType);
        if (!policyDecision.isAllowed()) {
            return policyDecision;
        }
        if (HIGH_SECRET.matcher(prompt).find()) {
            log.warn("Compression outbound blocked: conversationId={}, providerType={}, reason=HIGH_SECRET",
                    conversationId, providerType);
            return Decision.blocked("HIGH_SECRET");
        }
        String sanitized = CONTACT.matcher(prompt).replaceAll("[REDACTED_CONTACT]");
        sanitized = CN_ID.matcher(sanitized).replaceAll("[REDACTED_CN_ID]");
        sanitized = BANK_CARD.matcher(sanitized).replaceAll("[REDACTED_BANK_CARD]");
        boolean redacted = !StringUtils.equals(prompt, sanitized);
        if (redacted) {
            log.info("Compression outbound sanitized: conversationId={}, providerType={}, reason=PII_REDACTED",
                    conversationId, providerType);
            return Decision.allowed(sanitized, true, "PII_REDACTED");
        }
        return Decision.allowed(prompt, false, "ALLOWED");
    }

    /**
     * 校验供应商处理区域和数据处理许可。JSON 策略示例：
     * {"allowCompressionOutbound":true,"allowedRegions":["CN","GLOBAL"],"allowTraining":false}
     */
    private Decision reviewProviderPolicy(String conversationId, ModelProvider provider, String providerType) {
        if (provider == null) {
            return Decision.allowed(null, false, "NO_PROVIDER_POLICY");
        }
        String policy = StringUtils.trimToEmpty(provider.getDataProcessingPolicy());
        if (StringUtils.isBlank(policy)) {
            return Decision.allowed(null, false, "NO_PROVIDER_POLICY");
        }
        if (!StringUtils.startsWith(policy, "{")) {
            return Decision.allowed(null, false, "UNSTRUCTURED_PROVIDER_POLICY");
        }
        try {
            JSONObject json = JSON.parseObject(policy);
            if (!Boolean.TRUE.equals(json.getBoolean("allowCompressionOutbound"))) {
                return blockPolicy(conversationId, providerType, "POLICY_COMPRESSION_NOT_ALLOWED");
            }
            if (Boolean.TRUE.equals(json.getBoolean("allowTraining"))) {
                return blockPolicy(conversationId, providerType, "POLICY_TRAINING_ALLOWED");
            }
            String region = StringUtils.trimToEmpty(provider.getProcessingRegion());
            if (StringUtils.isBlank(region)) {
                return blockPolicy(conversationId, providerType, "POLICY_REGION_MISSING");
            }
            if (json.getJSONArray("allowedRegions") != null
                    && !json.getJSONArray("allowedRegions").contains(region)) {
                return blockPolicy(conversationId, providerType, "POLICY_REGION_NOT_ALLOWED");
            }
            return Decision.allowed(null, false, "PROVIDER_POLICY_ALLOWED");
        } catch (Exception e) {
            return blockPolicy(conversationId, providerType, "POLICY_INVALID_JSON");
        }
    }

    private Decision blockPolicy(String conversationId, String providerType, String reason) {
        log.warn("Compression outbound blocked: conversationId={}, providerType={}, reason={}",
                conversationId, providerType, reason);
        return Decision.blocked(reason);
    }

    /**
     * Governance decision for a compression-model outbound request.
     */
    public static class Decision {
        private final boolean allowed;
        private final String prompt;
        private final boolean redacted;
        private final String reason;

        private Decision(boolean allowed, String prompt, boolean redacted, String reason) {
            this.allowed = allowed;
            this.prompt = prompt;
            this.redacted = redacted;
            this.reason = reason;
        }

        public static Decision allowed(String prompt, boolean redacted, String reason) {
            return new Decision(true, prompt, redacted, reason);
        }

        public static Decision blocked(String reason) {
            return new Decision(false, null, false, reason);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getPrompt() {
            return prompt;
        }

        public boolean isRedacted() {
            return redacted;
        }

        public String getReason() {
            return reason;
        }
    }
}
