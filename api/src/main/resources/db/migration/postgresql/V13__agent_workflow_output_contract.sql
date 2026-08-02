-- 业务接入输出契约：已发布版本决定终态回调可暴露的字段。
ALTER TABLE agent_workflow ADD COLUMN IF NOT EXISTS output_schema TEXT;
ALTER TABLE agent_workflow_version ADD COLUMN IF NOT EXISTS output_schema TEXT;
UPDATE agent_workflow SET output_schema = '[]' WHERE output_schema IS NULL;
UPDATE agent_workflow_version SET output_schema = '[]' WHERE output_schema IS NULL;
