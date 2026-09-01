CREATE TABLE IF NOT EXISTS aether_workspace (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL REFERENCES aether_tenant(id),
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT aether_workspace_tenant_code_uk UNIQUE (tenant_id, code)
);
CREATE INDEX IF NOT EXISTS aether_workspace_tenant_idx ON aether_workspace(tenant_id, status, deleted);
