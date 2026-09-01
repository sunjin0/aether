INSERT INTO aether_solution (
    id, name, code, version, description, manifest_json, status, created_at, updated_at, sort_num, deleted, state, tenant_id
)
SELECT
    'solution-ai-sre-1',
    'AI SRE',
    'ai-sre',
    '1.0.0',
    '告警接入、诊断工作流、知识检索与人工审批修复的官方示例方案。',
    '{"schemaVersion":"1.0","displayName":"AI SRE","capabilities":["alert-webhook","diagnosis-workflow","knowledge-retrieval","human-approval"],"dependencies":[{"type":"connector","code":"prometheus","version":"1"},{"type":"connector","code":"grafana","version":"1"},{"type":"connector","code":"kubernetes","version":"1"},{"type":"skill","code":"sre-diagnosis","version":"1"}],"configuration":{"alertWebhook":{"required":true},"approval":{"required":true}}}',
    1,
    EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000,
    EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000,
    0,
    FALSE,
    0,
    NULL
WHERE NOT EXISTS (
    SELECT 1 FROM aether_solution WHERE code = 'ai-sre' AND version = '1.0.0' AND tenant_id IS NULL AND deleted = FALSE
);
