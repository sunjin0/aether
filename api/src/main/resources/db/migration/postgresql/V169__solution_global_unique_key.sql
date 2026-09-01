CREATE UNIQUE INDEX IF NOT EXISTS aether_solution_global_code_version_uk
    ON aether_solution(code, version)
    WHERE tenant_id IS NULL AND deleted = FALSE;
