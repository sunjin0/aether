-- Defaults are stored in the published template policy.  The control plane
-- still bounds every value before enforcing it.
UPDATE sandbox_execution_template_version
SET config_snapshot = (config_snapshot::jsonb || '{"maxConcurrentTasks":4,"maxConcurrentTasksPerAgent":2,"maxDailyTasksPerUser":100,"maxDailyTasksPerAgent":500}'::jsonb)::text
WHERE id = 'sandbox_generic_document_v1';
UPDATE sandbox_execution_template_version
SET config_snapshot = (config_snapshot::jsonb || '{"maxConcurrentTasks":2,"maxConcurrentTasksPerAgent":1,"maxDailyTasksPerUser":20,"maxDailyTasksPerAgent":100}'::jsonb)::text
WHERE id IN ('sandbox_local_python_v2', 'sandbox_local_node_v2', 'sandbox_python_check_v1', 'sandbox_node_check_v1');
