ALTER TABLE agent_definition ADD COLUMN IF NOT EXISTS smtp_sender_email varchar(255);
ALTER TABLE agent_definition ADD COLUMN IF NOT EXISTS smtp_host varchar(255);
ALTER TABLE agent_definition ADD COLUMN IF NOT EXISTS smtp_port integer;
ALTER TABLE agent_definition ADD COLUMN IF NOT EXISTS smtp_security varchar(16);
ALTER TABLE agent_definition ADD COLUMN IF NOT EXISTS smtp_authorization_code varchar(1024);

COMMENT ON COLUMN agent_definition.smtp_sender_email IS 'Agent-owned sender mailbox for external business email';
COMMENT ON COLUMN agent_definition.smtp_host IS 'Agent-owned SMTP host';
COMMENT ON COLUMN agent_definition.smtp_port IS 'Agent-owned SMTP port';
COMMENT ON COLUMN agent_definition.smtp_security IS 'Agent-owned SMTP transport security: ssl or starttls';
COMMENT ON COLUMN agent_definition.smtp_authorization_code IS 'AES encrypted Agent SMTP authorization code; never returned by APIs';
