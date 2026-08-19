CREATE TABLE IF NOT EXISTS agent_run_context_metric (
    model_call_id VARCHAR(32) NOT NULL PRIMARY KEY,
    source_model_call_id VARCHAR(32),
    run_id VARCHAR(32) NOT NULL,
    call_type VARCHAR(32) NOT NULL,
    attempt_no INTEGER NOT NULL DEFAULT 1,
    metric_phase VARCHAR(16) NOT NULL,
    context_window_tokens INTEGER NOT NULL,
    output_reserve_tokens INTEGER NOT NULL,
    safety_reserve_tokens INTEGER NOT NULL,
    input_budget_tokens INTEGER NOT NULL,
    prompt_tokens INTEGER,
    estimated_prompt_tokens INTEGER NOT NULL,
    system_tokens INTEGER NOT NULL DEFAULT 0,
    skill_tokens INTEGER NOT NULL DEFAULT 0,
    task_tokens INTEGER NOT NULL DEFAULT 0,
    memory_tokens INTEGER NOT NULL DEFAULT 0,
    summary_tokens INTEGER NOT NULL DEFAULT 0,
    history_tokens INTEGER NOT NULL DEFAULT 0,
    tool_tokens INTEGER NOT NULL DEFAULT 0,
    rag_tokens INTEGER NOT NULL DEFAULT 0,
    current_message_tokens INTEGER NOT NULL DEFAULT 0,
    trimmed_message_count INTEGER NOT NULL DEFAULT 0,
    compressed_message_count INTEGER NOT NULL DEFAULT 0,
    compression_status VARCHAR(32) NOT NULL DEFAULT 'NOT_NEEDED',
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS agent_run_context_metric_run_id ON agent_run_context_metric(run_id);

ALTER TABLE agent_message ADD COLUMN IF NOT EXISTS context_tokens INTEGER;
ALTER TABLE agent_message ADD COLUMN IF NOT EXISTS context_budget_tokens INTEGER;
