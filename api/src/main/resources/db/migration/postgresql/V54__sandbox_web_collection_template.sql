-- Phase 5 is intentionally disabled until a separately deployed egress proxy
-- enforces connect-time DNS/IP checks and the stated domain policy.
INSERT INTO sandbox_execution_template
    (id, code, name, description, enabled, risk_level, current_version_id, created_at, updated_at, sort_num, deleted, state)
VALUES ('sandbox_web_collection', 'web-collection', '网页采集', 'HTTPS 白名单网页采集（需受控 egress 代理）', FALSE, 'HIGH', 'sandbox_web_collection_v1',
 (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 40, FALSE, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO sandbox_execution_template_version
    (id, template_id, version, published, config_snapshot, policy_version, published_at, created_at, updated_at, sort_num, deleted, state)
VALUES ('sandbox_web_collection_v1', 'sandbox_web_collection', 1, TRUE,
 '{"runtime":"PYTHON","executionMode":"WEB_COLLECTION","network":"EGRESS_PROXY_REQUIRED","allowedDomains":["example.com"],"allowSubdomains":true,"allowedPorts":[443],"maxRequests":20,"maxConcurrentRequests":2,"maxPageDepth":1,"maxResponseBytes":5242880,"maxDownloadBytes":10485760,"maxScreenshots":3,"timeoutSeconds":120,"maxOutputFiles":5,"maxOutputBytes":52428800,"outputFormats":["json","csv","md","png"],"scriptSlot":false,"readOnlyRoot":true,"nonPrivileged":true}',
 'v1', (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 0, FALSE, 0)
ON CONFLICT DO NOTHING;
