-- Skill resources no longer provide executable artifact entry points.
-- Platform-managed artifact generation continues to use agent_sandbox_execution.
DROP TABLE IF EXISTS agent_skill_execution_config;
