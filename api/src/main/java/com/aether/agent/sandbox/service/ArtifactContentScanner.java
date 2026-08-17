package com.aether.agent.sandbox.service;

/**
 * Pluggable virus/malicious-content scanner; implementations must not retain plaintext.
 */
public interface ArtifactContentScanner {
    /**
     * 扫描文件内容是否包含病毒或恶意内容；实现不得保留明文内容。
     */
    ScanResult scan(String fileName, String contentType, byte[] content);

    /**
     * 文件内容扫描结果，包含是否允许继续处理及命中的规则编号。
     */
    final class ScanResult {
        private final boolean allowed;
        private final String ruleId;

        /**
         * 创建 {@code ScanResult} 实例。
         */
        private ScanResult(boolean allowed, String ruleId) {
            this.allowed = allowed;
            this.ruleId = ruleId;
        }

        /**
         * 处理allowed。
         */
        public static ScanResult allowed() {
            return new ScanResult(true, null);
        }

        /**
         * Allowed but auditable match, used for low-risk PII under a continue policy.
         */
        public static ScanResult flagged(String ruleId) {
            return new ScanResult(true, ruleId);
        }

        /**
         * 处理blocked。
         */
        public static ScanResult blocked(String ruleId) {
            return new ScanResult(false, ruleId);
        }

        /**
         * 判断是否为Allowed。
         */
        public boolean isAllowed() {
            return allowed;
        }

        /**
         * 获取RuleId。
         */
        public String getRuleId() {
            return ruleId;
        }
    }
}
