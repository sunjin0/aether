CREATE TABLE knowledge_retrieval_evaluation_label (
    id VARCHAR(32) PRIMARY KEY,
    evaluation_case_id VARCHAR(32) NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    document_id VARCHAR(32),
    section_path VARCHAR(512),
    chunk_id VARCHAR(32),
    relevance_grade INTEGER NOT NULL DEFAULT 1,
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    remark TEXT,
    status INTEGER NOT NULL DEFAULT 1,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE knowledge_retrieval_evaluation_set_version (
    id VARCHAR(32) PRIMARY KEY,
    evaluation_set_id VARCHAR(32) NOT NULL,
    version_no INTEGER NOT NULL,
    snapshot_json TEXT NOT NULL,
    published_at BIGINT NOT NULL,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_knowledge_eval_set_version UNIQUE (evaluation_set_id, version_no)
);

CREATE INDEX idx_knowledge_eval_label_case ON knowledge_retrieval_evaluation_label(evaluation_case_id) WHERE deleted=FALSE;
CREATE INDEX idx_knowledge_eval_set_version ON knowledge_retrieval_evaluation_set_version(evaluation_set_id, version_no DESC) WHERE deleted=FALSE;

ALTER TABLE knowledge_retrieval_evaluation_run
    ADD COLUMN evaluation_set_version_id VARCHAR(32);
