-- 审计口径拆分：记录 Deep 运行中等待用户输入/审批的人工耗时(毫秒)。
-- 执行耗时 = latency_ms - waiting_ms，避免"请求耗时"含人工等待失真。
ALTER TABLE agent_run
    ADD COLUMN IF NOT EXISTS waiting_ms BIGINT NOT NULL DEFAULT 0;
