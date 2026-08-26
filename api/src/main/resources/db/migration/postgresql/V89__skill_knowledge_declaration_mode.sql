ALTER TABLE agent_skill_knowledge_binding
    ADD COLUMN IF NOT EXISTS declaration_mode VARCHAR(32) NOT NULL DEFAULT 'RETRIEVE_ONLY';

COMMENT ON COLUMN agent_skill_knowledge_binding.declaration_mode IS
    'Knowledge prompt declaration: ALWAYS, ROUTE_MATCHED, RETRIEVE_ONLY';
