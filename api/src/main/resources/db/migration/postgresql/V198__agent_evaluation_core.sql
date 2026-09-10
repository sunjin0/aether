CREATE TABLE IF NOT EXISTS evaluation_dataset (
    id VARCHAR(32) PRIMARY KEY, name VARCHAR(128) NOT NULL, description TEXT,
    target_type VARCHAR(16) NOT NULL, owner_id VARCHAR(32), revision BIGINT NOT NULL DEFAULT 0,
    archived BOOLEAN NOT NULL DEFAULT FALSE, created_at BIGINT, updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT evaluation_dataset_target_ck CHECK (target_type IN ('AGENT','WORKFLOW'))
);
CREATE TABLE IF NOT EXISTS evaluation_case (
    id VARCHAR(32) PRIMARY KEY, dataset_id VARCHAR(32) NOT NULL, case_key VARCHAR(128) NOT NULL,
    name VARCHAR(128), input_json JSONB NOT NULL, reference_json JSONB, assertions_json JSONB,
    evaluator_bindings_json JSONB, tags_json JSONB, enabled BOOLEAN NOT NULL DEFAULT TRUE,
    required BOOLEAN NOT NULL DEFAULT FALSE, pass_threshold INTEGER NOT NULL DEFAULT 80, revision BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT evaluation_case_threshold_ck CHECK (pass_threshold BETWEEN 0 AND 100)
);
CREATE UNIQUE INDEX IF NOT EXISTS evaluation_case_active_key ON evaluation_case(dataset_id, case_key) WHERE deleted = FALSE;
CREATE TABLE IF NOT EXISTS evaluation_dataset_version (
    id VARCHAR(32) PRIMARY KEY, dataset_id VARCHAR(32) NOT NULL, version_no INTEGER NOT NULL,
    content_hash VARCHAR(64) NOT NULL, case_count INTEGER NOT NULL DEFAULT 0, published_by VARCHAR(32), published_at BIGINT,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS evaluation_dataset_version_uk ON evaluation_dataset_version(dataset_id, version_no) WHERE deleted = FALSE;
CREATE TABLE IF NOT EXISTS evaluation_case_version (
    id VARCHAR(32) PRIMARY KEY, dataset_version_id VARCHAR(32) NOT NULL, case_key VARCHAR(128) NOT NULL,
    name VARCHAR(128), input_json JSONB NOT NULL, reference_json JSONB, assertions_json JSONB,
    evaluator_bindings_json JSONB, tags_json JSONB, required BOOLEAN NOT NULL DEFAULT FALSE, pass_threshold INTEGER NOT NULL DEFAULT 80,
    content_hash VARCHAR(64) NOT NULL, created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS evaluation_case_version_uk ON evaluation_case_version(dataset_version_id, case_key) WHERE deleted = FALSE;
CREATE TABLE IF NOT EXISTS evaluation_evaluator (
    id VARCHAR(32) PRIMARY KEY, name VARCHAR(128) NOT NULL, kind VARCHAR(16) NOT NULL, owner_id VARCHAR(32),
    draft_config_json JSONB NOT NULL, revision BIGINT NOT NULL DEFAULT 0, archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT evaluation_evaluator_kind_ck CHECK (kind IN ('RULE','LLM'))
);
CREATE TABLE IF NOT EXISTS evaluation_evaluator_version (
    id VARCHAR(32) PRIMARY KEY, evaluator_id VARCHAR(32) NOT NULL, version_no INTEGER NOT NULL,
    config_json JSONB NOT NULL, content_hash VARCHAR(64) NOT NULL, published_by VARCHAR(32), published_at BIGINT,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS evaluation_evaluator_version_uk ON evaluation_evaluator_version(evaluator_id, version_no) WHERE deleted = FALSE;
CREATE TABLE IF NOT EXISTS evaluation_target_snapshot (
    id VARCHAR(32) PRIMARY KEY, target_type VARCHAR(16) NOT NULL, target_id VARCHAR(32) NOT NULL,
    source_kind VARCHAR(16) NOT NULL, source_version_id VARCHAR(32), snapshot_json JSONB NOT NULL,
    fingerprint VARCHAR(64) NOT NULL, created_by VARCHAR(32), created_at BIGINT, updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS evaluation_snapshot_target_idx ON evaluation_target_snapshot(target_type, target_id, created_at DESC);
