-- 0 表示不限制；限制包含运行中和等待人工操作的实例，防止长期人工节点突破业务容量预算。
ALTER TABLE agent_workflow ADD COLUMN IF NOT EXISTS max_concurrent_instances INTEGER NOT NULL DEFAULT 0;
