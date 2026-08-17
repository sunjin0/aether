CREATE TABLE IF NOT EXISTS knowledge_retrieval_log
(
    id                  VARCHAR(32) PRIMARY KEY,
    agent_definition_id VARCHAR(32),
    conversation_id     VARCHAR(32),
    message_id          VARCHAR(32),
    query_hash          VARCHAR(64) NOT NULL,
    knowledge_base_id   VARCHAR(32),
    document_id         VARCHAR(32),
    chunk_id            VARCHAR(32),
    similarity          DOUBLE PRECISION,
    retrieval_score     DOUBLE PRECISION,
    cited               BOOLEAN     NOT NULL DEFAULT FALSE,
    outcome             VARCHAR(16) NOT NULL,
    retrieved_at        BIGINT      NOT NULL,
    created_at          BIGINT,
    updated_at          BIGINT,
    sort_num            INTEGER     NOT NULL DEFAULT 0,
    deleted             BOOLEAN     NOT NULL DEFAULT FALSE,
    state               INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_knowledge_retrieval_log_query
    ON knowledge_retrieval_log (query_hash, retrieved_at DESC) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_knowledge_retrieval_log_chunk
    ON knowledge_retrieval_log (chunk_id, cited, retrieved_at DESC) WHERE deleted = FALSE;
