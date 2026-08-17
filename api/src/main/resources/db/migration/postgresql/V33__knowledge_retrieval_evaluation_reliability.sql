-- Preserve the inputs and outcomes of each evaluation run so historical results remain interpretable.
ALTER TABLE knowledge_retrieval_evaluation_case
    ADD COLUMN target_type VARCHAR(16) NOT NULL DEFAULT 'DOCUMENT',
    ADD COLUMN chunk_id VARCHAR(32);

ALTER TABLE knowledge_retrieval_evaluation_run
    ADD COLUMN status VARCHAR(24) NOT NULL DEFAULT 'SUCCEEDED',
    ADD COLUMN failed_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN dataset_snapshot TEXT,
    ADD COLUMN run_config_snapshot TEXT,
    ADD COLUMN error_summary_json TEXT;

ALTER TABLE knowledge_retrieval_evaluation_result
    ADD COLUMN question_snapshot TEXT,
    ADD COLUMN expected_document_id_snapshot VARCHAR(32),
    ADD COLUMN expected_section_path_snapshot VARCHAR(512),
    ADD COLUMN target_type_snapshot VARCHAR(16),
    ADD COLUMN expected_chunk_ids_snapshot TEXT,
    ADD COLUMN error_code VARCHAR(64),
    ADD COLUMN error_message TEXT;

CREATE INDEX idx_knowledge_eval_result_run ON knowledge_retrieval_evaluation_result(run_id, status) WHERE deleted=FALSE;
