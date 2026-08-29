CREATE TABLE IF NOT EXISTS agent_workflow_event_receipt (
    id VARCHAR(32) PRIMARY KEY,
    application_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_id VARCHAR(256) NOT NULL,
    correlation_key VARCHAR(256),
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS agent_workflow_event_receipt_uk
    ON agent_workflow_event_receipt(application_id, event_type, event_id) WHERE deleted = FALSE;
