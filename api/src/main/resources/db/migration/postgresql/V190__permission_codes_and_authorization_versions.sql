ALTER TABLE sys_resource ADD COLUMN IF NOT EXISTS code VARCHAR(128);
UPDATE sys_resource SET code = CASE
    WHEN path IS NOT NULL AND path <> '' THEN path
    ELSE id
END
WHERE code IS NULL OR code = '';
CREATE UNIQUE INDEX IF NOT EXISTS sys_resource_code_uq
    ON sys_resource(code) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS sys_authorization_version (
    id VARCHAR(32) PRIMARY KEY,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(32) NOT NULL,
    organization_id VARCHAR(32),
    department_id VARCHAR(32),
    version BIGINT NOT NULL DEFAULT 0,
    state INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    sort_num INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT sys_authorization_version_subject_ck CHECK (subject_type IN ('USER', 'SERVICE_ACCOUNT'))
);
CREATE UNIQUE INDEX IF NOT EXISTS sys_authorization_version_scope_uq
    ON sys_authorization_version(subject_type, subject_id,
                                  COALESCE(organization_id, ''), COALESCE(department_id, ''))
    WHERE deleted = FALSE;
