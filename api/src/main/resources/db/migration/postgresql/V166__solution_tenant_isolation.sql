ALTER TABLE aether_solution ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
ALTER TABLE aether_solution_installation ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS aether_solution_tenant_idx ON aether_solution(tenant_id, status, deleted);
CREATE INDEX IF NOT EXISTS aether_solution_install_tenant_idx ON aether_solution_installation(tenant_id, application_id, status, deleted);
