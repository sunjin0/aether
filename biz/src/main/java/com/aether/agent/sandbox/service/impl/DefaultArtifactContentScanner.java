package com.aether.agent.sandbox.service.impl;

import com.aether.agent.sandbox.service.ArtifactContentScanner;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Stateless local policy scanner. It returns only rule identifiers and never stores matched plaintext.
 */
@Component
public class DefaultArtifactContentScanner implements ArtifactContentScanner {
    private static final Pattern SECRET = Pattern.compile("(?is)\\\"?(api[_-]?key|token|secret|password)\\\"?\\s*[:=]\\s*[\\\"']?[^\\s,;}{\\\"]{8,}");
    private static final Pattern CN_ID = Pattern.compile("(?<![0-9Xx])[1-9][0-9]{5}(?:18|19|20)?[0-9]{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12][0-9]|3[01])[0-9]{3}[0-9Xx](?![0-9Xx])");
    private static final Pattern BANK = Pattern.compile("(?<![0-9])[0-9]{16,19}(?![0-9])");
    private static final Pattern EMAIL_OR_MOBILE = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}|(?<![0-9])1[3-9][0-9]{9}(?![0-9])");
    private final boolean enabled;

    /**
     * 创建 {@code DefaultArtifactContentScanner} 实例。
     */
    public DefaultArtifactContentScanner(@Value("${aether.sandbox.sensitive-scanner.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 处理scan。
     */
    @Override
    public ScanResult scan(String fileName, String contentType, byte[] content) {
        if (!enabled || content == null || content.length == 0 || !textual(fileName, contentType))
            return ScanResult.allowed();
        String sample = new String(content, 0, Math.min(content.length, 2 * 1024 * 1024), StandardCharsets.UTF_8);
        if (SECRET.matcher(sample).find()) return ScanResult.blocked("HIGH_SECRET");
        if (CN_ID.matcher(sample).find()) return ScanResult.blocked("HIGH_CN_ID");
        if (BANK.matcher(sample).find()) return ScanResult.blocked("HIGH_BANK_CARD");
        if (EMAIL_OR_MOBILE.matcher(sample).find()) return ScanResult.flagged("PII_CONTACT");
        return ScanResult.allowed();
    }

    /**
     * 处理textual。
     */
    private boolean textual(String name, String contentType) {
        String type = StringUtils.lowerCase(StringUtils.defaultString(contentType));
        if (type.startsWith("text/") || type.contains("json") || type.contains("xml") || type.contains("yaml"))
            return true;
        String lower = StringUtils.lowerCase(StringUtils.defaultString(name));
        return lower.endsWith(".csv") || lower.endsWith(".json") || lower.endsWith(".md") || lower.endsWith(".txt");
    }
}
