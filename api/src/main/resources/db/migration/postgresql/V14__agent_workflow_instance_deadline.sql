-- 业务人工处理 SLA：仅等待用户交互的实例由定时任务超时终止。
ALTER TABLE agent_workflow_instance ADD COLUMN IF NOT EXISTS deadline_at BIGINT;
CREATE INDEX IF NOT EXISTS agent_workflow_instance_idx_waiting_deadline
    ON agent_workflow_instance(status, deadline_at)
    WHERE deleted = FALSE AND deadline_at IS NOT NULL;
