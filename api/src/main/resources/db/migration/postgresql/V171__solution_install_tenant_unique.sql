-- Tenant isolation: installation uniqueness must not be global across tenants.
ALTER TABLE aether_solution_installation
    DROP CONSTRAINT IF EXISTS aether_solution_install_uk;

DROP INDEX IF EXISTS aether_solution_active_install_uk;

CREATE UNIQUE INDEX IF NOT EXISTS aether_solution_install_tenant_version_uk
    ON aether_solution_installation(tenant_id, solution_id, application_id, solution_version)
    WHERE deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS aether_solution_active_install_tenant_uk
    ON aether_solution_installation(tenant_id, solution_id, application_id)
    WHERE status = 1 AND deleted = FALSE;
