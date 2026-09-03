-- Remove menu and role grants for platform features that are no longer available.
WITH retired_resources AS (
    SELECT id
    FROM sys_resource
    WHERE id IN (
        'sys_tenant', 'sys_workspace', 'sys_project', 'sys_identity',
        'perm_sys_tenant_read', 'perm_sys_tenant_write',
        'perm_sys_workspace_read', 'perm_sys_workspace_write',
        'perm_sys_project_read', 'perm_sys_project_write',
        'perm_sys_identity_read', 'perm_sys_identity_write'
    )
    OR path IN ('/sys/tenant', '/sys/workspace', '/sys/project', '/sys/identity')
)
DELETE FROM sys_role_resource
WHERE resource_id IN (SELECT id FROM retired_resources);

DELETE FROM sys_resource
WHERE id IN (
    'sys_tenant', 'sys_workspace', 'sys_project', 'sys_identity',
    'perm_sys_tenant_read', 'perm_sys_tenant_write',
    'perm_sys_workspace_read', 'perm_sys_workspace_write',
    'perm_sys_project_read', 'perm_sys_project_write',
    'perm_sys_identity_read', 'perm_sys_identity_write'
)
OR path IN ('/sys/tenant', '/sys/workspace', '/sys/project', '/sys/identity');
