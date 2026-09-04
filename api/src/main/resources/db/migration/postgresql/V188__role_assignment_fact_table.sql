CREATE TABLE IF NOT EXISTS sys_role_assignment (
    id VARCHAR(32) PRIMARY KEY,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(32) NOT NULL,
    role_id VARCHAR(32) NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_id VARCHAR(32),
    organization_id VARCHAR(32),
    effective_from BIGINT,
    effective_to BIGINT,
    assigned_by VARCHAR(32),
    state INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    sort_num INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT sys_role_assignment_subject_ck CHECK (subject_type IN ('USER', 'SERVICE_ACCOUNT')),
    CONSTRAINT sys_role_assignment_scope_ck CHECK (scope_type IN ('PLATFORM', 'DEPARTMENT')),
    CONSTRAINT sys_role_assignment_scope_id_ck CHECK (
        (scope_type = 'PLATFORM' AND scope_id IS NULL)
        OR (scope_type = 'DEPARTMENT' AND scope_id IS NOT NULL AND organization_id IS NOT NULL)
    ),
    CONSTRAINT sys_role_assignment_effective_ck CHECK (
        effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS sys_role_assignment_active_uq
    ON sys_role_assignment(subject_type, subject_id, role_id, scope_type, COALESCE(scope_id, ''))
    WHERE deleted = FALSE AND state = 0;
CREATE INDEX IF NOT EXISTS sys_role_assignment_subject_idx
    ON sys_role_assignment(subject_type, subject_id, organization_id, scope_type, scope_id, state, deleted);
CREATE INDEX IF NOT EXISTS sys_role_assignment_scope_idx
    ON sys_role_assignment(organization_id, scope_type, scope_id, state, deleted);
