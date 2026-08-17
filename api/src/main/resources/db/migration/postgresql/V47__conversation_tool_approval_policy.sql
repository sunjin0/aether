ALTER TABLE agent_conversation
    ADD COLUMN IF NOT EXISTS tool_approval_policy VARCHAR (32) NOT NULL DEFAULT 'ask';

COMMENT ON COLUMN agent_conversation.tool_approval_policy IS
    'Session tool approval policy: ask, risky, never';
