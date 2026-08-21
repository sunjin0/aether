-- 服务账号重构为独立的外部 Agent 接入主体，不再绑定后台管理员用户。
ALTER TABLE sys_service_account DROP COLUMN IF EXISTS user_id;

ALTER TABLE sys_service_account
    ADD COLUMN IF NOT EXISTS allowed_agent_ids TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS max_agent_calls_per_hour INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS sys_service_account_idx_client_enabled
    ON sys_service_account(client_id, enabled) WHERE deleted = FALSE;
