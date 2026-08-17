-- Sandbox task platform control plane. Existing agent_sandbox_execution and
-- agent_artifact remain the compatibility projection for generate_artifact.
CREATE TABLE IF NOT EXISTS sandbox_execution_template
(
    id                 VARCHAR(32) PRIMARY KEY,
    code               VARCHAR(64)  NOT NULL,
    name               VARCHAR(128) NOT NULL,
    description        TEXT,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    risk_level         VARCHAR(16)  NOT NULL DEFAULT 'LOW',
    current_version_id VARCHAR(32),
    created_at         BIGINT,
    updated_at         BIGINT,
    sort_num           INTEGER      NOT NULL DEFAULT 0,
    deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    state              INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS sandbox_execution_template_uk_code
    ON sandbox_execution_template(code) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS sandbox_execution_template_version
(
    id              VARCHAR(32) PRIMARY KEY,
    template_id     VARCHAR(32) NOT NULL,
    version         INTEGER     NOT NULL,
    published       BOOLEAN     NOT NULL DEFAULT FALSE,
    config_snapshot TEXT        NOT NULL,
    policy_version  VARCHAR(64) NOT NULL DEFAULT 'v1',
    published_by    VARCHAR(32),
    published_at    BIGINT,
    created_at      BIGINT,
    updated_at      BIGINT,
    sort_num        INTEGER     NOT NULL DEFAULT 0,
    deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    state           INTEGER     NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS sandbox_execution_template_version_uk
    ON sandbox_execution_template_version(template_id, version) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS sandbox_execution_task
(
    id                   VARCHAR(32) PRIMARY KEY,
    legacy_execution_id  VARCHAR(32),
    template_id          VARCHAR(32) NOT NULL,
    template_version_id  VARCHAR(32) NOT NULL,
    template_code        VARCHAR(64) NOT NULL,
    requester_user_id    VARCHAR(32) NOT NULL,
    agent_definition_id  VARCHAR(32),
    run_id               VARCHAR(64),
    message_id           VARCHAR(32),
    status               VARCHAR(24) NOT NULL,
    risk_level           VARCHAR(16) NOT NULL,
    approval_required    BOOLEAN     NOT NULL DEFAULT TRUE,
    input_snapshot       TEXT        NOT NULL,
    input_sha256         VARCHAR(64) NOT NULL,
    script_sha256        VARCHAR(64),
    config_snapshot      TEXT        NOT NULL,
    policy_version       VARCHAR(64) NOT NULL,
    execution_token_hash VARCHAR(64),
    claimed_by           VARCHAR(128),
    claimed_at           BIGINT,
    lease_expires_at     BIGINT,
    cancel_requested_at  BIGINT,
    started_at           BIGINT,
    completed_at         BIGINT,
    expires_at           BIGINT      NOT NULL,
    failure_code         VARCHAR(64),
    failure_reason       TEXT,
    log_summary          TEXT,
    created_at           BIGINT,
    updated_at           BIGINT,
    sort_num             INTEGER     NOT NULL DEFAULT 0,
    deleted              BOOLEAN     NOT NULL DEFAULT FALSE,
    state                INTEGER     NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS sandbox_execution_task_uk_legacy
    ON sandbox_execution_task(legacy_execution_id) WHERE legacy_execution_id IS NOT NULL AND deleted = FALSE;
CREATE INDEX IF NOT EXISTS sandbox_execution_task_idx_claim
    ON sandbox_execution_task(status, expires_at, created_at) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS sandbox_execution_task_idx_lease
    ON sandbox_execution_task(lease_expires_at) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS sandbox_execution_task_idx_requester
    ON sandbox_execution_task(requester_user_id, created_at DESC) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS sandbox_execution_approval
(
    id               VARCHAR(32) PRIMARY KEY,
    task_id          VARCHAR(32) NOT NULL,
    decision         VARCHAR(16) NOT NULL,
    approver_user_id VARCHAR(32) NOT NULL,
    reason           VARCHAR(1024),
    decided_at       BIGINT      NOT NULL,
    created_at       BIGINT,
    updated_at       BIGINT,
    sort_num         INTEGER     NOT NULL DEFAULT 0,
    deleted          BOOLEAN     NOT NULL DEFAULT FALSE,
    state            INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS sandbox_execution_approval_idx_task
    ON sandbox_execution_approval(task_id, decided_at DESC) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS sandbox_execution_event
(
    id          VARCHAR(32) PRIMARY KEY,
    task_id     VARCHAR(32) NOT NULL,
    sequence    BIGINT      NOT NULL,
    event_type  VARCHAR(32) NOT NULL,
    status      VARCHAR(24),
    progress    INTEGER,
    summary     TEXT,
    occurred_at BIGINT      NOT NULL,
    created_at  BIGINT,
    updated_at  BIGINT,
    sort_num    INTEGER     NOT NULL DEFAULT 0,
    deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
    state       INTEGER     NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS sandbox_execution_event_uk_task_sequence
    ON sandbox_execution_event(task_id, sequence) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS sandbox_execution_event_idx_task
    ON sandbox_execution_event(task_id, occurred_at) WHERE deleted = FALSE;

-- Built-in low-risk document template. The runner receives only this frozen
-- config; no request can override runtime, image, network or resource limits.
INSERT INTO sandbox_execution_template
(id, code, name, description, enabled, risk_level, created_at, updated_at, sort_num, deleted, state)
VALUES ('sandbox_generic_document', 'generic-document', '通用文档生成', '固定办公运行时的 DOCX/XLSX/PDF 生成', TRUE,
        'LOW',
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 0, FALSE, 0)
ON CONFLICT
DO NOTHING;
INSERT INTO sandbox_execution_template_version
(id, template_id, version, published, config_snapshot, policy_version, published_at, created_at, updated_at, sort_num,
 deleted, state)
VALUES ('sandbox_generic_document_v1', 'sandbox_generic_document', 1, TRUE,
        '{"runtime":"PYTHON","outputFormats":["docx","xlsx","pdf"],"timeoutSeconds":60,"maxOutputFiles":1,"maxOutputBytes":52428800,"network":"NONE","scriptSlot":false}',
        'v1', (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 0, FALSE, 0)
ON CONFLICT
DO NOTHING;
UPDATE sandbox_execution_template
SET current_version_id = 'sandbox_generic_document_v1'
WHERE id = 'sandbox_generic_document'
  AND current_version_id IS NULL;
