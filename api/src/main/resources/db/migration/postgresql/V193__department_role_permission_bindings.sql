-- 部门角色仅表示身份；权限改为由各部门独立配置。
CREATE TABLE IF NOT EXISTS sys_department_role_resource (
    id VARCHAR(32) PRIMARY KEY,
    department_id VARCHAR(32) NOT NULL,
    role_id VARCHAR(32) NOT NULL,
    resource_id VARCHAR(32) NOT NULL,
    state INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    sort_num INTEGER NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS sys_department_role_resource_uq
    ON sys_department_role_resource(department_id, role_id, resource_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS sys_department_role_resource_lookup_idx
    ON sys_department_role_resource(department_id, role_id, state, deleted);

-- 首次迁移保持现有部门角色权限行为；后续修改只作用于具体部门。
INSERT INTO sys_department_role_resource (id, department_id, role_id, resource_id,
                                          state, deleted, created_at, updated_at, sort_num)
SELECT md5('department-role-resource:' || d.id || ':' || rr.role_id || ':' || rr.resource_id),
       d.id, rr.role_id, rr.resource_id, 0, FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM sys_department d
JOIN sys_role r ON r.scope = 'DEPARTMENT' AND r.deleted = FALSE AND r.state = 0
JOIN sys_role_resource rr ON rr.role_id = r.id AND rr.deleted = FALSE AND rr.state = 0
WHERE d.deleted = FALSE AND d.state = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_department_role_resource drr
      WHERE drr.department_id = d.id AND drr.role_id = rr.role_id AND drr.resource_id = rr.resource_id
        AND drr.deleted = FALSE
  );
