CREATE TABLE IF NOT EXISTS sys_authorization_audit (
    id VARCHAR(32) PRIMARY KEY,
    action VARCHAR(64) NOT NULL,
    actor_id VARCHAR(32),
    subject_type VARCHAR(32),
    subject_id VARCHAR(32),
    role_id VARCHAR(32),
    scope_type VARCHAR(32),
    scope_id VARCHAR(32),
    organization_id VARCHAR(32),
    success BOOLEAN NOT NULL DEFAULT TRUE,
    reason VARCHAR(512),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    sort_num INTEGER NOT NULL DEFAULT 1,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS sys_authorization_audit_scope_idx
    ON sys_authorization_audit(organization_id, created_at DESC);
CREATE INDEX IF NOT EXISTS sys_authorization_audit_subject_idx
    ON sys_authorization_audit(subject_type, subject_id, created_at DESC);
