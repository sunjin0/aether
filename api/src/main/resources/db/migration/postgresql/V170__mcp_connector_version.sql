ALTER TABLE agent_mcp_server ADD COLUMN IF NOT EXISTS version VARCHAR(32);
UPDATE agent_mcp_server SET version = '1.0.0' WHERE version IS NULL OR BTRIM(version) = '';
ALTER TABLE agent_mcp_server ALTER COLUMN version SET NOT NULL;
