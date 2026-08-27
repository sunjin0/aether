-- A product version is immutable once published. All versions of one product share product_id.
ALTER TABLE agent_product_profile ADD COLUMN IF NOT EXISTS product_id VARCHAR(64);
UPDATE agent_product_profile SET product_id = id WHERE product_id IS NULL;
ALTER TABLE agent_product_profile ALTER COLUMN product_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS agent_product_profile_ix_product_version
    ON agent_product_profile(product_id, version_no) WHERE deleted = FALSE;
