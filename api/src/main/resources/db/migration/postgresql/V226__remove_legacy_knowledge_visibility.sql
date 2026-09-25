-- KnowledgeBase scope (PLATFORM/AGENT) was the retired visibility boundary.
-- Account ownership is now the only data boundary.
ALTER TABLE IF EXISTS knowledge_base DROP COLUMN IF EXISTS scope;
DROP INDEX IF EXISTS knowledge_base_idx_scope;
