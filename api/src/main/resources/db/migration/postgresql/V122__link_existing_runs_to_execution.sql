ALTER TABLE agent_run ADD COLUMN IF NOT EXISTS execution_id VARCHAR(32);
ALTER TABLE agent_tool_call_log ADD COLUMN IF NOT EXISTS execution_id VARCHAR(32);
ALTER TABLE agent_workflow_instance ADD COLUMN IF NOT EXISTS execution_id VARCHAR(32);

CREATE INDEX IF NOT EXISTS agent_run_execution_idx ON agent_run(execution_id);
CREATE INDEX IF NOT EXISTS agent_tool_call_execution_idx ON agent_tool_call_log(execution_id);
CREATE INDEX IF NOT EXISTS agent_workflow_instance_execution_idx ON agent_workflow_instance(execution_id);
