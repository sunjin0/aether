-- Tenant/workspace/project catalog and enterprise identity pages were retired
-- in V178. Remove their grants and resources together so dynamic menus cannot
-- expose dead routes on upgraded installations.
DELETE FROM sys_role_resource
WHERE resource_id IN (
    'sys_tenant', 'sys_workspace', 'sys_project', 'sys_identity',
    'perm_sys_tenant_read', 'perm_sys_tenant_write',
    'perm_sys_workspace_read', 'perm_sys_workspace_write',
    'perm_sys_project_read', 'perm_sys_project_write',
    'perm_sys_identity_read', 'perm_sys_identity_write'
);

DELETE FROM sys_resource
WHERE id IN (
    'sys_tenant', 'sys_workspace', 'sys_project', 'sys_identity',
    'perm_sys_tenant_read', 'perm_sys_tenant_write',
    'perm_sys_workspace_read', 'perm_sys_workspace_write',
    'perm_sys_project_read', 'perm_sys_project_write',
    'perm_sys_identity_read', 'perm_sys_identity_write'
);
