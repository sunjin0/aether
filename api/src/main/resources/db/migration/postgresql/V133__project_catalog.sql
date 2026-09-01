CREATE TABLE IF NOT EXISTS aether_project (
    id VARCHAR(32) PRIMARY KEY,
    workspace_id VARCHAR(32) NOT NULL REFERENCES aether_workspace(id),
    application_id VARCHAR(32) REFERENCES agent_application(id),
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT aether_project_workspace_code_uk UNIQUE (workspace_id, code)
);
CREATE INDEX IF NOT EXISTS aether_project_workspace_idx ON aether_project(workspace_id, status, deleted);
