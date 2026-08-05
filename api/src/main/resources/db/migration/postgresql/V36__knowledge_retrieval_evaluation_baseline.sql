ALTER TABLE knowledge_retrieval_evaluation_run
    ADD COLUMN is_baseline BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uq_knowledge_eval_run_baseline
    ON knowledge_retrieval_evaluation_run(evaluation_set_id) WHERE deleted=FALSE AND is_baseline=TRUE;
