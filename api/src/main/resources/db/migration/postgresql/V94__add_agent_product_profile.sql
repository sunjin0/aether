CREATE TABLE IF NOT EXISTS agent_product_profile (
 id VARCHAR(64) PRIMARY KEY, application_id VARCHAR(64) NOT NULL, agent_definition_id VARCHAR(64) NOT NULL,
 product_type VARCHAR(32) NOT NULL, name VARCHAR(128) NOT NULL, input_schema TEXT, output_schema TEXT,
 knowledge_policy TEXT, approval_policy TEXT, handoff_policy TEXT, status INTEGER NOT NULL DEFAULT 0,
 version_no INTEGER NOT NULL DEFAULT 0, published_at BIGINT, created_at BIGINT, updated_at BIGINT,
 sort_num INTEGER DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS agent_product_profile_uk_agent_type ON agent_product_profile(application_id, agent_definition_id, product_type) WHERE deleted = FALSE;
