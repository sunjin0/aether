-- Compatibility tasks are created before the final assistant message exists.
-- Backfill historic tasks so chat approval cards can attach deterministically.
UPDATE sandbox_execution_task task
SET message_id = run.message_id FROM agent_run run
WHERE task.run_id = run.id
  AND task.message_id IS NULL
  AND run.message_id IS NOT NULL;
