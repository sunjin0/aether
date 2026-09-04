CREATE TABLE IF NOT EXISTS sys_organization (
 id VARCHAR(32) PRIMARY KEY, code VARCHAR(64) NOT NULL, name VARCHAR(255) NOT NULL,
 owner_id VARCHAR(32) NOT NULL, state INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE,
 created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, sort_num INTEGER NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS sys_organization_code_uq ON sys_organization(code) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS sys_team (
 id VARCHAR(32) PRIMARY KEY, organization_id VARCHAR(32) NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(255) NOT NULL,
 state INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, created_at BIGINT NOT NULL,
 updated_at BIGINT NOT NULL, sort_num INTEGER NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS sys_team_org_code_uq ON sys_team(organization_id, code) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS sys_team_org_idx ON sys_team(organization_id, deleted, state);

CREATE TABLE IF NOT EXISTS sys_organization_member (
 id VARCHAR(32) PRIMARY KEY, organization_id VARCHAR(32) NOT NULL, user_id VARCHAR(32) NOT NULL,
 role_code VARCHAR(32) NOT NULL, source VARCHAR(32) NOT NULL DEFAULT 'DIRECT', state INTEGER NOT NULL DEFAULT 0,
 deleted BOOLEAN NOT NULL DEFAULT FALSE, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, sort_num INTEGER NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS sys_org_member_uq ON sys_organization_member(organization_id,user_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS sys_org_member_user_idx ON sys_organization_member(user_id,deleted,state);

CREATE TABLE IF NOT EXISTS sys_team_member (
 id VARCHAR(32) PRIMARY KEY, organization_id VARCHAR(32) NOT NULL, team_id VARCHAR(32) NOT NULL,
 user_id VARCHAR(32) NOT NULL, role_code VARCHAR(32) NOT NULL DEFAULT 'MEMBER', state INTEGER NOT NULL DEFAULT 0,
 deleted BOOLEAN NOT NULL DEFAULT FALSE, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, sort_num INTEGER NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS sys_team_member_uq ON sys_team_member(team_id,user_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS sys_team_member_user_idx ON sys_team_member(user_id,organization_id,deleted,state);

CREATE TABLE IF NOT EXISTS sys_invitation (
 id VARCHAR(32) PRIMARY KEY, organization_id VARCHAR(32) NOT NULL, team_id VARCHAR(32), email VARCHAR(255) NOT NULL,
 role_code VARCHAR(32) NOT NULL, token_hash VARCHAR(128) NOT NULL, expires_at BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING', inviter_id VARCHAR(32) NOT NULL, state INTEGER NOT NULL DEFAULT 0,
 deleted BOOLEAN NOT NULL DEFAULT FALSE, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, sort_num INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS sys_invitation_lookup_idx ON sys_invitation(email,status,expires_at);

ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS scope VARCHAR(16) NOT NULL DEFAULT 'PLATFORM';
CREATE INDEX IF NOT EXISTS sys_role_scope_idx ON sys_role(scope,tenant_id,deleted);

INSERT INTO sys_role (id,name,description,scope,state,deleted,created_at,updated_at,sort_num)
SELECT md5('organization-role-'||x.role_scope||'-'||x.code),x.code,x.description,x.role_scope,0,FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,1
FROM (VALUES ('OWNER','组织所有者','ORGANIZATION'),('ORG_ADMIN','组织管理员','ORGANIZATION'),('MEMBER','成员','ORGANIZATION'),('READ_ONLY','只读成员','ORGANIZATION'),('TEAM_ADMIN','团队管理员','TEAM'),('MEMBER','成员','TEAM'),('READ_ONLY','只读成员','TEAM')) x(code,description,role_scope)
WHERE NOT EXISTS (SELECT 1 FROM sys_role r WHERE r.name=x.code AND r.scope=x.role_scope AND r.deleted=FALSE);

-- 将没有旧租户归属的用户迁移到唯一的默认组织；旧 tenant/workspace/project 数据不参与授权。
INSERT INTO sys_organization (id,code,name,owner_id,state,deleted,created_at,updated_at,sort_num)
SELECT md5('default-organization'), 'default', '默认组织', u.id, 0, FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,1
FROM sys_user u WHERE u.username='admin' AND u.deleted=FALSE
  AND NOT EXISTS (SELECT 1 FROM sys_organization WHERE code='default' AND deleted=FALSE)
LIMIT 1;
INSERT INTO sys_organization_member (id,organization_id,user_id,role_code,source,state,deleted,created_at,updated_at,sort_num)
SELECT md5('default-member-'||u.id), o.id, u.id,
       CASE WHEN u.username='admin' THEN 'OWNER' ELSE 'READ_ONLY' END, 'MIGRATION', 0, FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,(EXTRACT(EPOCH FROM clock_timestamp())*1000)::BIGINT,1
FROM sys_user u CROSS JOIN (SELECT id FROM sys_organization WHERE code='default' AND deleted=FALSE LIMIT 1) o
WHERE u.deleted=FALSE AND NOT EXISTS (SELECT 1 FROM sys_organization_member m WHERE m.organization_id=o.id AND m.user_id=u.id AND m.deleted=FALSE);
