ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS smtp_host varchar(255);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS smtp_port integer;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS smtp_security varchar(16);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS smtp_authorization_code varchar(1024);

COMMENT ON COLUMN sys_user.smtp_host IS 'User-owned SMTP host for agent email sending';
COMMENT ON COLUMN sys_user.smtp_port IS 'User-owned SMTP port';
COMMENT ON COLUMN sys_user.smtp_security IS 'SMTP transport security: ssl or starttls';
COMMENT ON COLUMN sys_user.smtp_authorization_code IS 'AES encrypted SMTP authorization code; never returned by APIs';
