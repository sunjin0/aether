ALTER TABLE agent_skill_version
    ADD COLUMN IF NOT EXISTS routing_keywords TEXT;
