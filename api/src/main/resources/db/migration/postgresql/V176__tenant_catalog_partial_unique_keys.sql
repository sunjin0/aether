-- Keep soft-deleted catalog entries reusable while preserving active-code uniqueness.
ALTER TABLE aether_tenant DROP CONSTRAINT IF EXISTS aether_tenant_code_key;
CREATE UNIQUE INDEX IF NOT EXISTS aether_tenant_active_code_uk
    ON aether_tenant(code) WHERE deleted = FALSE;

ALTER TABLE aether_workspace DROP CONSTRAINT IF EXISTS aether_workspace_tenant_code_uk;
CREATE UNIQUE INDEX IF NOT EXISTS aether_workspace_active_tenant_code_uk
    ON aether_workspace(tenant_id, code) WHERE deleted = FALSE;

ALTER TABLE aether_project DROP CONSTRAINT IF EXISTS aether_project_workspace_code_uk;
CREATE UNIQUE INDEX IF NOT EXISTS aether_project_active_workspace_code_uk
    ON aether_project(workspace_id, code) WHERE deleted = FALSE;
