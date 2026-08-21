ALTER TABLE agent_run ADD COLUMN IF NOT EXISTS raw_response TEXT;
COMMENT ON COLUMN agent_run.raw_response IS '模型供应商原始响应，用于运行审计和用量诊断';
