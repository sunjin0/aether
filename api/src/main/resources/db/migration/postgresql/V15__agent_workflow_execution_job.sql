-- 持久化工作流推进任务：请求只创建实例，耗时 Agent/MCP 执行由后台工作者消费。
CREATE TABLE IF NOT EXISTS agent_workflow_execution_job
(
    id              VARCHAR(32) PRIMARY KEY,
    instance_id     VARCHAR(32) NOT NULL,
    status          VARCHAR(24) NOT NULL,
    attempt_count   INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at BIGINT,
    locked_at       BIGINT,
    error_message   VARCHAR(2048),
    completed_at    BIGINT,
    created_at      BIGINT,
    updated_at      BIGINT,
    sort_num        INTEGER     NOT NULL DEFAULT 0,
    deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    state           INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS agent_workflow_execution_job_idx_pending
    ON agent_workflow_execution_job(status, next_attempt_at, locked_at)
    WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_workflow_execution_job_idx_instance
    ON agent_workflow_execution_job(instance_id, status)
    WHERE deleted = FALSE;
