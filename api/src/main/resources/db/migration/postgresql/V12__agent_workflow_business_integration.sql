-- 工作流业务接入：业务关联、请求幂等和终态回调投递审计。
ALTER TABLE agent_workflow_instance
    ADD COLUMN IF NOT EXISTS business_type VARCHAR (64);
ALTER TABLE agent_workflow_instance
    ADD COLUMN IF NOT EXISTS business_id VARCHAR (128);
ALTER TABLE agent_workflow_instance
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR (128);
ALTER TABLE agent_workflow_instance
    ADD COLUMN IF NOT EXISTS callback_url VARCHAR (2048);
CREATE UNIQUE INDEX IF NOT EXISTS agent_workflow_instance_uk_idempotency
    ON agent_workflow_instance(workflow_id, user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_workflow_instance_idx_business
    ON agent_workflow_instance(business_type, business_id, created_at DESC)
    WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS agent_workflow_callback_delivery
(
    id              VARCHAR(32) PRIMARY KEY,
    instance_id     VARCHAR(32)   NOT NULL,
    event_type      VARCHAR(48)   NOT NULL,
    callback_url    VARCHAR(2048) NOT NULL,
    payload         TEXT          NOT NULL,
    status          VARCHAR(24)   NOT NULL,
    attempt_count   INTEGER       NOT NULL DEFAULT 0,
    response_status INTEGER,
    response_body   VARCHAR(2048),
    error_message   VARCHAR(2048),
    next_attempt_at BIGINT,
    delivered_at    BIGINT,
    created_at      BIGINT,
    updated_at      BIGINT,
    sort_num        INTEGER       NOT NULL DEFAULT 0,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    state           INTEGER       NOT NULL DEFAULT 0,
    UNIQUE (instance_id, event_type)
);
CREATE INDEX IF NOT EXISTS agent_workflow_callback_delivery_idx_pending
    ON agent_workflow_callback_delivery(status, next_attempt_at)
    WHERE deleted = FALSE;
