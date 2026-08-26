CREATE TABLE IF NOT EXISTS agent_product_profile_version (
 id VARCHAR(64) PRIMARY KEY, profile_id VARCHAR(64) NOT NULL, version_no INTEGER NOT NULL, snapshot TEXT NOT NULL,
 published_by VARCHAR(64), published_at BIGINT NOT NULL, created_at BIGINT, updated_at BIGINT,
 sort_num INTEGER DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS agent_product_profile_version_uk ON agent_product_profile_version(profile_id, version_no) WHERE deleted = FALSE;
