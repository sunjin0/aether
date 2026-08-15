ALTER TABLE agent_session ADD COLUMN IF NOT EXISTS graph_thread_id VARCHAR(32);
UPDATE agent_session SET graph_thread_id = id WHERE graph_thread_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS agent_session_graph_thread_uk
    ON agent_session(graph_thread_id) WHERE graph_thread_id IS NOT NULL AND deleted = FALSE;
