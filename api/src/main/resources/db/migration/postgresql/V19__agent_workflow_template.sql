CREATE TABLE IF NOT EXISTS agent_workflow_template
(
    id                  VARCHAR(32) PRIMARY KEY,
    name                VARCHAR(128) NOT NULL,
    description         VARCHAR(1024),
    agent_definition_id VARCHAR(32),
    nodes               TEXT         NOT NULL,
    edges               TEXT         NOT NULL,
    input_schema        TEXT         NOT NULL DEFAULT '[]',
    output_schema       TEXT         NOT NULL DEFAULT '[]',
    source_workflow_id  VARCHAR(32),
    source_version      INTEGER,
    created_at          BIGINT,
    updated_at          BIGINT,
    sort_num            INTEGER      NOT NULL DEFAULT 0,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    state               INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS agent_workflow_template_idx_name ON agent_workflow_template(name) WHERE deleted = FALSE;
