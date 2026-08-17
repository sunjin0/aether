-- 可验签的外部事件 Webhook 触发器。
CREATE TABLE IF NOT EXISTS agent_workflow_webhook_trigger
(
    id                         VARCHAR(32) PRIMARY KEY,
    workflow_id                VARCHAR(32)  NOT NULL,
    service_account_id         VARCHAR(32)  NOT NULL,
    name                       VARCHAR(128) NOT NULL,
    business_type              VARCHAR(64)  NOT NULL,
    business_id_expression     VARCHAR(512) NOT NULL,
    idempotency_key_expression VARCHAR(512) NOT NULL,
    variable_mapping           TEXT         NOT NULL DEFAULT '{}',
    signing_secret             TEXT         NOT NULL,
    enabled                    BOOLEAN      NOT NULL DEFAULT TRUE,
    last_triggered_at          BIGINT,
    last_error_message         VARCHAR(2048),
    created_at                 BIGINT,
    updated_at                 BIGINT,
    sort_num                   INTEGER      NOT NULL DEFAULT 0,
    deleted                    BOOLEAN      NOT NULL DEFAULT FALSE,
    state                      INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS agent_workflow_webhook_trigger_idx_workflow
    ON agent_workflow_webhook_trigger(workflow_id) WHERE deleted = FALSE;
