CREATE TABLE IF NOT EXISTS aether_solution (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(64) NOT NULL,
    version VARCHAR(32) NOT NULL,
    description VARCHAR(1000),
    manifest_json TEXT,
    status INTEGER NOT NULL DEFAULT 1,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT aether_solution_code_version_uk UNIQUE (code, version)
);
