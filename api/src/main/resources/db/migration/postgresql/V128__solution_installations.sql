CREATE TABLE IF NOT EXISTS aether_solution_installation (
    id VARCHAR(32) PRIMARY KEY,
    solution_id VARCHAR(32) NOT NULL,
    application_id VARCHAR(32) NOT NULL,
    solution_version VARCHAR(32) NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT aether_solution_install_uk UNIQUE (solution_id, application_id, solution_version)
);
CREATE INDEX IF NOT EXISTS aether_solution_install_app_idx ON aether_solution_installation(application_id, status, deleted);
