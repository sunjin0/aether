-- SCIM provisioning is idempotent at the service layer; this index closes the concurrent-create race.
CREATE UNIQUE INDEX IF NOT EXISTS sys_user_tenant_username_uk
    ON sys_user(tenant_id, username)
    WHERE deleted = FALSE;
