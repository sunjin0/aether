ALTER TABLE agent_skill_version
    ADD COLUMN IF NOT EXISTS routing_summary VARCHAR(200),
    ADD COLUMN IF NOT EXISTS trigger_terms TEXT,
    ADD COLUMN IF NOT EXISTS exclude_terms TEXT,
    ADD COLUMN IF NOT EXISTS routing_examples TEXT;

CREATE TABLE IF NOT EXISTS agent_skill_routing_index (
    id VARCHAR(32) PRIMARY KEY,
    skill_version_id VARCHAR(32) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    embedding_provider_id VARCHAR(32),
    embedding_model VARCHAR(128),
    embedding vector(1536),
    index_status SMALLINT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(512),
    indexed_at BIGINT,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS agent_skill_routing_index_uk_version ON agent_skill_routing_index(skill_version_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_skill_routing_index_idx_status ON agent_skill_routing_index(index_status, updated_at DESC) WHERE deleted = FALSE;
