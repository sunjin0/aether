-- Keep result display independent from document or chunk changes after a run has completed.
ALTER TABLE knowledge_retrieval_evaluation_result
    ADD COLUMN expected_document_title_snapshot VARCHAR(512),
    ADD COLUMN retrieved_items_snapshot         TEXT;
