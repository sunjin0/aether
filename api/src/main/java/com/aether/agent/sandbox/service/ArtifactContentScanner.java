package com.aether.agent.sandbox.service;

/** Pluggable virus/malicious-content scanner; implementations must not retain plaintext. */
public interface ArtifactContentScanner {
    ScanResult scan(String fileName, String contentType, byte[] content);
    final class ScanResult {
        private final boolean allowed;
        private final String ruleId;
        private ScanResult(boolean allowed, String ruleId) { this.allowed = allowed; this.ruleId = ruleId; }
        public boolean isAllowed() { return allowed; }
        public String getRuleId() { return ruleId; }
        public static ScanResult allowed() { return new ScanResult(true, null); }
        /** Allowed but auditable match, used for low-risk PII under a continue policy. */
        public static ScanResult flagged(String ruleId) { return new ScanResult(true, ruleId); }
        public static ScanResult blocked(String ruleId) { return new ScanResult(false, ruleId); }
    }
}
