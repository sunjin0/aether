-- 业务资源的名称、编码和模型标识只要求在创建账号范围内唯一。
-- 系统管理表不参与本迁移；父子关系型幂等键保持原有约束。

DROP INDEX IF EXISTS agent_application_uk_code;
CREATE UNIQUE INDEX agent_application_uk_code
    ON agent_application(created_by, code)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS agent_definition_uk_code;
CREATE UNIQUE INDEX agent_definition_uk_code
    ON agent_definition(created_by, code)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS agent_mcp_server_uk_code;
CREATE UNIQUE INDEX agent_mcp_server_uk_code
    ON agent_mcp_server(created_by, code)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS agent_model_provider_uk_name;
CREATE UNIQUE INDEX agent_model_provider_uk_name
    ON agent_model_provider(created_by, name)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS agent_model_catalog_provider_name_uk;
CREATE UNIQUE INDEX agent_model_catalog_provider_name_uk
    ON agent_model_catalog(created_by, provider_id, name)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS agent_product_profile_uk_application_code;
CREATE UNIQUE INDEX agent_product_profile_uk_application_code
    ON agent_product_profile(created_by, application_id, code)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS agent_skill_uk_code;
CREATE UNIQUE INDEX agent_skill_uk_code
    ON agent_skill(created_by, code)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS agent_tool_uk_code;
CREATE UNIQUE INDEX agent_tool_uk_code
    ON agent_tool(created_by, code)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS agent_workflow_uk_application_code;
CREATE UNIQUE INDEX agent_workflow_uk_application_code
    ON agent_workflow(created_by, application_id, code)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS agent_workflow_capability_code_uk;
CREATE UNIQUE INDEX agent_workflow_capability_code_uk
    ON agent_workflow_capability(created_by, application_id, capability_code)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

ALTER TABLE agent_evaluation_policy DROP CONSTRAINT IF EXISTS agent_evaluation_policy_uk_target;
DROP INDEX IF EXISTS agent_evaluation_policy_uk_target;
CREATE UNIQUE INDEX agent_evaluation_policy_uk_target
    ON agent_evaluation_policy(created_by, target_type, target_id)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS evaluation_case_active_key;
CREATE UNIQUE INDEX evaluation_case_active_key
    ON evaluation_case(created_by, dataset_id, case_key)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS evaluation_case_version_uk;
CREATE UNIQUE INDEX evaluation_case_version_uk
    ON evaluation_case_version(created_by, dataset_version_id, case_key)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS evaluation_dataset_version_uk;
CREATE UNIQUE INDEX evaluation_dataset_version_uk
    ON evaluation_dataset_version(created_by, dataset_id, version_no)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS evaluation_evaluator_version_uk;
CREATE UNIQUE INDEX evaluation_evaluator_version_uk
    ON evaluation_evaluator_version(created_by, evaluator_id, version_no)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS evaluation_result_unit_uk;
CREATE UNIQUE INDEX evaluation_result_unit_uk
    ON evaluation_result(created_by, experiment_id, case_version_id, repeat_index)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS evaluation_score_binding_uk;
CREATE UNIQUE INDEX evaluation_score_binding_uk
    ON evaluation_score(created_by, result_id, grading_round, binding_key)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

DROP INDEX IF EXISTS evaluation_task_phase_uk;
CREATE UNIQUE INDEX evaluation_task_phase_uk
    ON evaluation_task(created_by, result_id, phase, grading_round)
    WHERE deleted = FALSE AND created_by IS NOT NULL;

ALTER TABLE knowledge_document_version DROP CONSTRAINT IF EXISTS uk_knowledge_document_version;
DROP INDEX IF EXISTS uk_knowledge_document_version;
CREATE UNIQUE INDEX uk_knowledge_document_version
    ON knowledge_document_version(created_by, knowledge_document_id, version_no, deleted)
    WHERE created_by IS NOT NULL;

DROP INDEX IF EXISTS uk_knowledge_document_version_one_open;
CREATE UNIQUE INDEX uk_knowledge_document_version_one_open
    ON knowledge_document_version(created_by, knowledge_document_id)
    WHERE deleted = FALSE AND created_by IS NOT NULL
      AND review_status IN ('DRAFT', 'AI_REVIEWING', 'AI_REVIEWED', 'SUBMITTED');

DROP INDEX IF EXISTS uk_knowledge_document_chunk_version_active;
CREATE UNIQUE INDEX uk_knowledge_document_chunk_version_active
    ON knowledge_document_chunk(created_by, document_version_id, chunk_index)
    WHERE deleted = FALSE AND document_version_id IS NOT NULL AND created_by IS NOT NULL;

DROP INDEX IF EXISTS uq_knowledge_eval_run_baseline;
CREATE UNIQUE INDEX uq_knowledge_eval_run_baseline
    ON knowledge_retrieval_evaluation_run(created_by, evaluation_set_id)
    WHERE deleted = FALSE AND is_baseline = TRUE AND created_by IS NOT NULL;

ALTER TABLE knowledge_retrieval_evaluation_set_version DROP CONSTRAINT IF EXISTS uq_knowledge_eval_set_version;
DROP INDEX IF EXISTS uq_knowledge_eval_set_version;
CREATE UNIQUE INDEX uq_knowledge_eval_set_version
    ON knowledge_retrieval_evaluation_set_version(created_by, evaluation_set_id, version_no)
    WHERE created_by IS NOT NULL;

DROP INDEX IF EXISTS uk_knowledge_review_task_one_open;
CREATE UNIQUE INDEX uk_knowledge_review_task_one_open
    ON knowledge_review_task(created_by, document_id)
    WHERE deleted = FALSE AND created_by IS NOT NULL
      AND status IN ('pending', 'claimed');

DROP INDEX IF EXISTS sandbox_execution_template_uk_code;
CREATE UNIQUE INDEX sandbox_execution_template_uk_code
    ON sandbox_execution_template(created_by, code)
    WHERE deleted = FALSE AND created_by IS NOT NULL;
