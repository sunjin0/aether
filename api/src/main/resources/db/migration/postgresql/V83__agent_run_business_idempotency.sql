-- Enforce external business Agent idempotency before dispatching a run.
CREATE UNIQUE INDEX IF NOT EXISTS agent_run_uk_business_external_run
    ON agent_run(agent_definition_id, user_id, external_run_id)
    WHERE external_run_id IS NOT NULL AND deleted = FALSE;
