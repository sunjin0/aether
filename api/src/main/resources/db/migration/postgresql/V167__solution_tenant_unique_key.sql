ALTER TABLE aether_solution DROP CONSTRAINT IF EXISTS aether_solution_code_version_uk;
CREATE UNIQUE INDEX IF NOT EXISTS aether_solution_tenant_code_version_uk
    ON aether_solution(tenant_id, code, version);
