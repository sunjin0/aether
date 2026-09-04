-- 组织/团队权限数据的数据库级约束，保持 forward-only。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sys_role_scope_ck') THEN
        ALTER TABLE sys_role ADD CONSTRAINT sys_role_scope_ck
            CHECK (scope IN ('PLATFORM', 'ORGANIZATION', 'TEAM'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sys_invitation_status_ck') THEN
        ALTER TABLE sys_invitation ADD CONSTRAINT sys_invitation_status_ck
            CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED'));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS sys_invitation_token_hash_uq
    ON sys_invitation(token_hash) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS sys_org_member_role_idx
    ON sys_organization_member(organization_id, role_code, deleted, state);

CREATE INDEX IF NOT EXISTS sys_team_member_role_idx
    ON sys_team_member(organization_id, team_id, role_code, deleted, state);
