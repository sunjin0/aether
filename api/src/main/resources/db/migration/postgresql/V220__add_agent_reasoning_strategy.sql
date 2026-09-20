-- Make the standard Agent loop an explicit, backwards-compatible strategy.
ALTER TABLE agent_definition
    ADD COLUMN IF NOT EXISTS reasoning_strategy VARCHAR(16) NOT NULL DEFAULT 'REACT';

COMMENT ON COLUMN agent_definition.reasoning_strategy IS
    'Standard Agent strategy: DIRECT performs one model call; REACT permits model-tool-observation iterations';
