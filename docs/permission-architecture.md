# 权限与组织架构边界

系统权限只使用平台全局 RBAC：

```text
sys_user -> sys_user_role -> sys_role -> sys_role_resource -> sys_resource
```

组织、部门和成员仅描述公司的组织架构与人员归属：

```text
sys_organization -> sys_department -> sys_department_member
```

部门成员的 `identity_code`（如 `DEPARTMENT_ADMIN`、`MEMBER`、`READ_ONLY`）仅供界面展示和组织管理流程使用，不会合并到菜单、接口或业务数据权限。组织架构管理接口仍由平台 RBAC 的 `/sys/organization` 资源保护。

已废弃组织/部门作用域授权表、角色分配、授权版本和授权审计表。它们通过前向 Flyway 迁移删除；历史迁移文件保持不变，以保证已部署环境的迁移链完整。
