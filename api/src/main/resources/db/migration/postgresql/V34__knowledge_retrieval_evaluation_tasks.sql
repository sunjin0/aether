ALTER TABLE knowledge_retrieval_evaluation_run
    ADD COLUMN agent_definition_id_snapshot VARCHAR(32);

CREATE TABLE knowledge_retrieval_evaluation_task
(
    id                             VARCHAR(32) PRIMARY KEY,
    run_id                         VARCHAR(32) NOT NULL,
    evaluation_case_id             VARCHAR(32) NOT NULL,
    question_snapshot              TEXT        NOT NULL,
    target_type_snapshot           VARCHAR(16) NOT NULL,
    expected_chunk_ids_snapshot    TEXT        NOT NULL,
    expected_document_id_snapshot  VARCHAR(32),
    expected_section_path_snapshot VARCHAR(512),
    status                         VARCHAR(24) NOT NULL,
    attempt_count                  INTEGER     NOT NULL DEFAULT 0,
    max_attempts                   INTEGER     NOT NULL DEFAULT 3,
    error_code                     VARCHAR(64),
    error_message                  TEXT,
    started_at                     BIGINT,
    finished_at                    BIGINT,
    created_at                     BIGINT,
    updated_at                     BIGINT,
    sort_num                       INTEGER     NOT NULL DEFAULT 0,
    deleted                        BOOLEAN     NOT NULL DEFAULT FALSE,
    state                          INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX idx_knowledge_eval_task_dispatch
    ON knowledge_retrieval_evaluation_task (status, created_at) WHERE deleted=FALSE;
CREATE INDEX idx_knowledge_eval_task_run
    ON knowledge_retrieval_evaluation_task (run_id, status) WHERE deleted=FALSE;
