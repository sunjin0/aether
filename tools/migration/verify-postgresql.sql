-- Run after PostgreSQL schema initialization and, for production, after data import.

SELECT extname, extversion
FROM pg_extension
WHERE extname = 'vector';

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('sys_user', 'sys_dict', 'knowledge_document', 'knowledge_document_chunk')
ORDER BY table_name;

SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN ('idx_knowledge_document_chunk_embedding_cosine', 'idx_knowledge_document_chunk_knowledge_base',
                    'idx_knowledge_document_chunk_content_fts')
ORDER BY indexname;

SELECT 'sys_user' AS table_name,
       COUNT(*)   AS row_count,
       MIN(id)    AS min_id,
       MAX(id)    AS max_id,
       COUNT(*)      FILTER (WHERE deleted) AS deleted_count
FROM sys_user
UNION ALL
SELECT 'sys_dict', COUNT(*), MIN(id), MAX(id), COUNT(*) FILTER (WHERE deleted)
FROM sys_dict
UNION ALL
SELECT 'knowledge_document', COUNT(*), MIN(id), MAX(id), COUNT(*) FILTER (WHERE deleted)
FROM knowledge_document
UNION ALL
SELECT 'knowledge_document_chunk', COUNT(*), MIN(id), MAX(id), COUNT(*) FILTER (WHERE deleted)
FROM knowledge_document_chunk;

-- Verify the pgvector operator and HNSW index without retaining test data.
BEGIN;
INSERT INTO knowledge_document_chunk (id, knowledge_base_id, document_id, chunk_index, content, token_count, embedding,
                                      created_at, updated_at, sort_num, deleted, state)
VALUES (900000000000000001, 1, 1, 0, 'vector smoke test A', 4, array_fill(0::real, ARRAY[1536])::vector,
        0, 0, 0, FALSE, 0),
       (900000000000000002, 1, 1, 1, 'vector smoke test B', 4, array_fill(1::real, ARRAY[1536])::vector,
        0, 0, 0, FALSE, 0);
EXPLAIN
    (COSTS OFF) SELECT id
FROM knowledge_document_chunk
WHERE knowledge_base_id = 1 AND deleted = FALSE
ORDER BY embedding <=> array_fill(0::real, ARRAY[1536])::vector
LIMIT 1;
SET LOCAL enable_seqscan = off;
EXPLAIN
    (COSTS OFF) SELECT id
FROM knowledge_document_chunk
ORDER BY embedding <=> array_fill(0::real, ARRAY[1536])::vector
LIMIT 1;
EXPLAIN
    (COSTS OFF) SELECT id
FROM knowledge_document_chunk
WHERE to_tsvector('simple', content) @@ plainto_tsquery('simple', 'vector')
ORDER BY ts_rank_cd(to_tsvector('simple', content), plainto_tsquery('simple', 'vector'))
DESC
    LIMIT 1;
ROLLBACK;
