-- Store per-knowledge-base semantic chunking limits. Existing knowledge bases use application defaults.
ALTER TABLE knowledge_base
    ADD COLUMN IF NOT EXISTS chunking_config TEXT;

COMMENT ON COLUMN knowledge_base.chunking_config IS
    'Chunking JSON: strategy (SEMANTIC, MARKDOWN, PARAGRAPH or FIXED_LENGTH), maxChars, overlapChars and maxTokens';
