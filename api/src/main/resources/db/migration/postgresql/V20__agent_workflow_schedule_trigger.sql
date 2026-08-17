-- 持久化定时触发器。next_fire_at/locked_until 允许多实例 Worker 通过租约安全竞争同一触发任务。
CREATE TABLE IF NOT EXISTS agent_workflow_schedule_trigger (
    id VARCHAR(32) PRIMARY KEY, workflow_id VARCHAR(32) NOT NULL, service_account_id VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL, cron_expression VARCHAR(128) NOT NULL,
    business_type VARCHAR(64) NOT NULL, business_id_template VARCHAR(512) NOT NULL,
    variables TEXT NOT NULL DEFAULT '{}', enabled BOOLEAN NOT NULL DEFAULT TRUE,
    next_fire_at BIGINT NOT NULL, locked_until BIGINT, last_triggered_at BIGINT, last_error_message VARCHAR(2048),
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS agent_workflow_schedule_trigger_idx_due
    ON agent_workflow_schedule_trigger(next_fire_at) WHERE deleted = FALSE AND enabled = TRUE;
