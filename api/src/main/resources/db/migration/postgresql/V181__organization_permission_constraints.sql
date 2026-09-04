-- 强化组织/团队授权数据的数据库级约束，避免绕过 REST API 写入非法作用域。
ALTER TABLE sys_organization
    ADD CONSTRAINT sys_organization_code_ck CHECK (length(trim(code)) > 0),
    ADD CONSTRAINT sys_organization_name_ck CHECK (length(trim(name)) > 0);

ALTER TABLE sys_team
    ADD CONSTRAINT sys_team_code_ck CHECK (length(trim(code)) > 0),
    ADD CONSTRAINT sys_team_name_ck CHECK (length(trim(name)) > 0);

ALTER TABLE sys_organization_member
    ADD CONSTRAINT sys_org_member_role_ck CHECK (role_code IN ('OWNER', 'ORG_ADMIN', 'MEMBER', 'READ_ONLY'));

ALTER TABLE sys_team_member
    ADD CONSTRAINT sys_team_member_role_ck CHECK (role_code IN ('TEAM_ADMIN', 'MEMBER', 'READ_ONLY'));

ALTER TABLE sys_invitation
    ADD CONSTRAINT sys_invitation_status_ck CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    ADD CONSTRAINT sys_invitation_role_ck CHECK (role_code IN ('OWNER', 'ORG_ADMIN', 'MEMBER', 'READ_ONLY', 'TEAM_ADMIN'));

CREATE UNIQUE INDEX IF NOT EXISTS sys_invitation_token_hash_uq ON sys_invitation(token_hash);
