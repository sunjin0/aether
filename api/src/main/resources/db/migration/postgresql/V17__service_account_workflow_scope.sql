-- 服务账号工作流范围与调用额度治理。
ALTER TABLE sys_service_account ADD COLUMN IF NOT EXISTS allowed_workflow_ids TEXT NOT NULL DEFAULT '[]';
ALTER TABLE sys_service_account ADD COLUMN IF NOT EXISTS max_starts_per_hour INTEGER NOT NULL DEFAULT 0;
