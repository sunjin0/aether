CREATE TABLE IF NOT EXISTS sys_oidc_identity_binding (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64),
    issuer VARCHAR(512) NOT NULL,
    subject VARCHAR(512) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    email_snapshot VARCHAR(320),
    last_login_at BIGINT,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER DEFAULT 0,
    deleted BOOLEAN DEFAULT FALSE,
    state INTEGER DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_oidc_identity_binding_subject
    ON sys_oidc_identity_binding (tenant_id, issuer, subject) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_sys_oidc_identity_binding_user
    ON sys_oidc_identity_binding (tenant_id, user_id) WHERE deleted = FALSE;
