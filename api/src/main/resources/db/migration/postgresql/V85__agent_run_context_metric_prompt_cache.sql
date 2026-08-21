ALTER TABLE agent_run_context_metric ADD COLUMN IF NOT EXISTS cached_prompt_tokens INTEGER;
ALTER TABLE agent_run_context_metric ADD COLUMN IF NOT EXISTS uncached_prompt_tokens INTEGER;
ALTER TABLE agent_run_context_metric ADD COLUMN IF NOT EXISTS prompt_cache_hit_rate DOUBLE PRECISION;
