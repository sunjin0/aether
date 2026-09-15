-- 会话来源：控制台、外部接口、工作流节点。工作流节点会话只用于承载节点调用，
-- 不能在对话调试的会话列表里出现，因此来源必须显式落库而不是靠调用方推断。
ALTER TABLE agent_conversation
    ADD COLUMN IF NOT EXISTS source VARCHAR(32) NOT NULL DEFAULT 'CONSOLE';

-- 历史数据按既有判定规则回填：服务账号发起的外部调用，其余都来自控制台。
UPDATE agent_conversation
SET source = 'EXTERNAL',
    updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE source = 'CONSOLE'
  AND user_id LIKE 'sa:%';

-- 工作流 Agent 节点的历史会话标题只由 resolveNodeConversation 写入，据此回填，
-- 否则旧节点会话会继续出现在对话调试的会话列表里。
UPDATE agent_conversation
SET source = 'WORKFLOW',
    updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE source = 'CONSOLE'
  AND title LIKE '工作流节点 %';

COMMENT ON COLUMN agent_conversation.source IS '会话来源：CONSOLE-控制台/调试、EXTERNAL-外部接口、WORKFLOW-工作流节点';
