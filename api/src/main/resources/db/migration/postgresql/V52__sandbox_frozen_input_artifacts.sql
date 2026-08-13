-- Phase 2: task inputs are copied into a task-private object prefix.  The
-- database snapshot carries metadata and hash only; runners fetch bytes via a
-- lease-authorized API and never receive arbitrary object keys or host paths.
INSERT INTO sandbox_execution_template_version
    (id, template_id, version, published, config_snapshot, policy_version, published_at, created_at, updated_at, sort_num, deleted, state)
VALUES
 ('sandbox_local_python_v2', 'sandbox_local_python', 2, TRUE,
  '{"runtime":"PYTHON","scriptSlot":true,"network":"NONE","inputFormats":["csv","json","txt","md","pdf","xlsx","docx"],"maxInputFiles":5,"outputFormats":["csv","json","md"],"timeoutSeconds":120,"maxOutputFiles":10,"maxOutputBytes":52428800,"maxInputBytes":10485760,"readOnlyRoot":true,"nonPrivileged":true}',
  'v2', (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 0, FALSE, 0),
 ('sandbox_local_node_v2', 'sandbox_local_node', 2, TRUE,
  '{"runtime":"NODE","scriptSlot":true,"network":"NONE","inputFormats":["csv","json","txt","md","pdf","xlsx","docx"],"maxInputFiles":5,"outputFormats":["csv","json","md"],"timeoutSeconds":120,"maxOutputFiles":10,"maxOutputBytes":52428800,"maxInputBytes":10485760,"readOnlyRoot":true,"nonPrivileged":true}',
  'v2', (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 0, FALSE, 0)
ON CONFLICT DO NOTHING;
UPDATE sandbox_execution_template SET current_version_id = 'sandbox_local_python_v2' WHERE id = 'sandbox_local_python';
UPDATE sandbox_execution_template SET current_version_id = 'sandbox_local_node_v2' WHERE id = 'sandbox_local_node';
