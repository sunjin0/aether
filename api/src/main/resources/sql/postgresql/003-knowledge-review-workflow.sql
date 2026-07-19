-- Strict knowledge document review workflow migration.
-- Run once after 001-schema.sql and 002-data.sql, or against an existing Aether database.
\set ON_ERROR_STOP on

BEGIN;

DO $$
DECLARE
    default_owner_id VARCHAR(32);
    review_provider_id VARCHAR(32);
    review_model VARCHAR(128);
BEGIN
    SELECT id INTO default_owner_id
    FROM sys_user
    WHERE username = 'admin' AND deleted = FALSE
    ORDER BY created_at
    LIMIT 1;
    IF default_owner_id IS NULL THEN
        RAISE EXCEPTION 'active admin user is required before migrating knowledge-base ownership';
    END IF;

    SELECT id, default_model INTO review_provider_id, review_model
    FROM agent_model_provider
    WHERE status = 1 AND deleted = FALSE
      AND COALESCE(default_model, '') NOT ILIKE '%embedding%'
    ORDER BY sort, created_at
    LIMIT 1;
    IF review_provider_id IS NULL OR COALESCE(review_model, '') = '' THEN
        RAISE EXCEPTION 'an enabled non-embedding model provider is required for AI review';
    END IF;

    UPDATE knowledge_base
    SET owner_admin_id = default_owner_id
    WHERE owner_admin_id IS NULL OR BTRIM(owner_admin_id) = '';

    UPDATE knowledge_base
    SET review_config = jsonb_build_object(
            'autoAiReview', TRUE,
            'aiReviewRequired', TRUE,
            'blockOnCriticalIssues', TRUE,
            'requireDifferentApprover', TRUE,
            'reviewModelProviderId', review_provider_id,
            'reviewModel', review_model
        )::TEXT
    WHERE review_config IS NULL OR BTRIM(review_config) = '';
END $$;

ALTER TABLE knowledge_base ALTER COLUMN owner_admin_id SET NOT NULL;
ALTER TABLE knowledge_base ALTER COLUMN review_config SET NOT NULL;

-- The document root exposes only the published body. Draft content lives in versions.
UPDATE knowledge_document document
SET content = version.content
FROM knowledge_document_version version
WHERE version.knowledge_document_id = document.id
  AND version.version_no = document.current_version_no
  AND version.deleted = FALSE;
UPDATE knowledge_document SET content = NULL WHERE current_version_no = 0;

-- Close legacy/orphan open versions that are no longer referenced by the document aggregate.
UPDATE knowledge_document_version version
SET review_status = 'REJECTED',
    review_comment = 'migration: superseded orphan draft',
    reviewed_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
FROM knowledge_document document
WHERE version.knowledge_document_id = document.id
  AND version.deleted = FALSE
  AND version.review_status IN ('DRAFT', 'AI_REVIEWING', 'AI_REVIEWED', 'SUBMITTED')
  AND version.id IS DISTINCT FROM document.draft_version_id
  AND version.id IS DISTINCT FROM document.submitted_version_id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_document_version_one_open
    ON knowledge_document_version (knowledge_document_id)
    WHERE deleted = FALSE
      AND review_status IN ('DRAFT', 'AI_REVIEWING', 'AI_REVIEWED', 'SUBMITTED');

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_review_task_one_open
    ON knowledge_review_task (document_id)
    WHERE deleted = FALSE AND status IN ('pending', 'claimed');

COMMIT;
