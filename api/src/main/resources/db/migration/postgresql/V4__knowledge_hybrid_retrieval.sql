-- Hybrid retrieval: lexical candidate recall complements pgvector semantic recall.
-- The simple configuration is portable and particularly effective for product codes,
-- identifiers, dates, and whitespace-delimited terms. Deploy a language-specific
-- dictionary separately when the environment provides one.
CREATE INDEX IF NOT EXISTS idx_knowledge_document_chunk_content_fts
    ON knowledge_document_chunk
    USING gin (to_tsvector('simple', content));

