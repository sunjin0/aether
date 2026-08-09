-- A run can intentionally create more than one artifact (for example, a PDF
-- together with an XLSX export).  Execution id, rather than run id, is the
-- idempotency boundary for a single generate_artifact request.
DROP INDEX IF EXISTS agent_sandbox_execution_uk_run;

CREATE INDEX IF NOT EXISTS agent_sandbox_execution_idx_run_created
    ON agent_sandbox_execution (run_id, created_at DESC);
