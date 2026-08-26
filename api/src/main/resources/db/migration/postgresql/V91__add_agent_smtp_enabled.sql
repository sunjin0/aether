ALTER TABLE agent_definition ADD COLUMN IF NOT EXISTS smtp_enabled boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN agent_definition.smtp_enabled IS 'Whether this Agent may use send_email; disabled Agents cannot fall back to user email settings';
