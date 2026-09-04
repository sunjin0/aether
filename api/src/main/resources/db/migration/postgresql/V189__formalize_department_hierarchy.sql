CREATE TABLE IF NOT EXISTS sys_department (
    id VARCHAR(32) PRIMARY KEY,
    organization_id VARCHAR(32) NOT NULL,
    parent_id VARCHAR(32),
    code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    manager_user_id VARCHAR(32),
    path VARCHAR(2048),
    level INTEGER NOT NULL DEFAULT 1,
    state INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    sort_num INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT sys_department_level_ck CHECK (level BETWEEN 1 AND 8)
);
CREATE UNIQUE INDEX IF NOT EXISTS sys_department_org_code_uq
    ON sys_department(organization_id, code) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS sys_department_tree_idx
    ON sys_department(organization_id, parent_id, state, deleted, sort_num);

CREATE TABLE IF NOT EXISTS sys_department_member (
    id VARCHAR(32) PRIMARY KEY,
    organization_id VARCHAR(32) NOT NULL,
    department_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    primary_department BOOLEAN NOT NULL DEFAULT FALSE,
    position_name VARCHAR(128),
    state INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    sort_num INTEGER NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS sys_department_member_uq
    ON sys_department_member(department_id, user_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS sys_department_member_user_idx
    ON sys_department_member(user_id, organization_id, state, deleted);

-- V182 已建立的旧约束不包含正式部门作用域；先扩展约束，再迁移 TEAM 角色。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sys_role_scope_ck') THEN
        ALTER TABLE sys_role DROP CONSTRAINT sys_role_scope_ck;
    END IF;
    ALTER TABLE sys_role ADD CONSTRAINT sys_role_scope_ck
        CHECK (scope IN ('PLATFORM', 'ORGANIZATION', 'TEAM', 'DEPARTMENT'));
END $$;

-- 旧 sys_team 是平铺模型；迁移为正式部门根节点，保留原 ID 便于回溯。
INSERT INTO sys_department (id, organization_id, parent_id, code, name, path, level, state, deleted,
                            created_at, updated_at, sort_num)
SELECT t.id, t.organization_id, NULL, t.code, t.name, '/' || t.id, 1, t.state, t.deleted,
       t.created_at, t.updated_at, t.sort_num
FROM sys_team t
WHERE NOT EXISTS (SELECT 1 FROM sys_department d WHERE d.id = t.id);

-- 组织成员是部门成员的前置条件；不为已移出组织的旧关系创建有效部门成员。
INSERT INTO sys_department_member (id, organization_id, department_id, user_id, primary_department,
                                   state, deleted, created_at, updated_at, sort_num)
SELECT tm.id, tm.organization_id, tm.team_id, tm.user_id, FALSE, tm.state, tm.deleted,
       tm.created_at, tm.updated_at, tm.sort_num
FROM sys_team_member tm
WHERE EXISTS (
    SELECT 1 FROM sys_organization_member om
    WHERE om.organization_id = tm.organization_id AND om.user_id = tm.user_id
      AND om.state = 0 AND om.deleted = FALSE
)
AND NOT EXISTS (SELECT 1 FROM sys_department_member dm WHERE dm.id = tm.id);

-- 将旧字符串角色转换为正式部门作用域的角色分配记录。
UPDATE sys_role SET scope = 'DEPARTMENT'
WHERE scope = 'TEAM' AND name IN ('DEPARTMENT_ADMIN', 'MEMBER', 'READ_ONLY') AND deleted = FALSE;

INSERT INTO sys_role_assignment (id, subject_type, subject_id, role_id, scope_type, scope_id,
                                 organization_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('department-assignment-' || tm.id), 'USER', tm.user_id, r.id, 'DEPARTMENT', tm.team_id,
       tm.organization_id, tm.state, tm.deleted, tm.created_at, tm.updated_at, tm.sort_num
FROM sys_team_member tm
JOIN sys_role r ON r.name = tm.role_code AND r.scope = 'DEPARTMENT' AND r.deleted = FALSE
WHERE EXISTS (
    SELECT 1 FROM sys_department_member dm
    WHERE dm.department_id = tm.team_id AND dm.user_id = tm.user_id
      AND dm.deleted = FALSE AND dm.state = 0
)
AND NOT EXISTS (
    SELECT 1 FROM sys_role_assignment ra
    WHERE ra.subject_type = 'USER' AND ra.subject_id = tm.user_id AND ra.role_id = r.id
      AND ra.scope_type = 'DEPARTMENT' AND ra.scope_id = tm.team_id AND ra.deleted = FALSE
);
