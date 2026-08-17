CREATE TABLE knowledge_retrieval_evaluation_set
(
    id                  VARCHAR(32) PRIMARY KEY,
    agent_definition_id VARCHAR(32)  NOT NULL,
    name                VARCHAR(128) NOT NULL,
    description         TEXT,
    status              INTEGER      NOT NULL DEFAULT 1,
    created_at          BIGINT,
    updated_at          BIGINT,
    sort_num            INTEGER      NOT NULL DEFAULT 0,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    state               INTEGER      NOT NULL DEFAULT 0
);
CREATE TABLE knowledge_retrieval_evaluation_case
(
    id                VARCHAR(32) PRIMARY KEY,
    evaluation_set_id VARCHAR(32) NOT NULL,
    question          TEXT        NOT NULL,
    document_id       VARCHAR(32),
    section_path      VARCHAR(512),
    remark            TEXT,
    status            INTEGER     NOT NULL DEFAULT 1,
    created_at        BIGINT,
    updated_at        BIGINT,
    sort_num          INTEGER     NOT NULL DEFAULT 0,
    deleted           BOOLEAN     NOT NULL DEFAULT FALSE,
    state             INTEGER     NOT NULL DEFAULT 0
);
CREATE TABLE knowledge_retrieval_evaluation_run
(
    id                        VARCHAR(32) PRIMARY KEY,
    evaluation_set_id         VARCHAR(32) NOT NULL,
    retrieval_config_snapshot TEXT,
    total_count               INTEGER     NOT NULL DEFAULT 0,
    invalid_count             INTEGER     NOT NULL DEFAULT 0,
    recall_at_k               DOUBLE PRECISION,
    mrr                       DOUBLE PRECISION,
    ndcg                      DOUBLE PRECISION,
    started_at                BIGINT      NOT NULL,
    finished_at               BIGINT,
    created_at                BIGINT,
    updated_at                BIGINT,
    sort_num                  INTEGER     NOT NULL DEFAULT 0,
    deleted                   BOOLEAN     NOT NULL DEFAULT FALSE,
    state                     INTEGER     NOT NULL DEFAULT 0
);
CREATE TABLE knowledge_retrieval_evaluation_result
(
    id                  VARCHAR(32) PRIMARY KEY,
    run_id              VARCHAR(32) NOT NULL,
    evaluation_case_id  VARCHAR(32) NOT NULL,
    status              VARCHAR(16) NOT NULL,
    retrieved_chunk_ids TEXT,
    recall_at_k         DOUBLE PRECISION,
    mrr                 DOUBLE PRECISION,
    ndcg                DOUBLE PRECISION,
    created_at          BIGINT,
    updated_at          BIGINT,
    sort_num            INTEGER     NOT NULL DEFAULT 0,
    deleted             BOOLEAN     NOT NULL DEFAULT FALSE,
    state               INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX idx_knowledge_eval_case_set ON knowledge_retrieval_evaluation_case (evaluation_set_id) WHERE deleted=FALSE;
CREATE INDEX idx_knowledge_eval_run_set ON knowledge_retrieval_evaluation_run (evaluation_set_id, started_at DESC) WHERE deleted=FALSE;
