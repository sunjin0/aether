-- Phase 6 immutable execution-usage observations. Null means the backend did
-- not expose a metric; it is deliberately not substituted with an estimate.
CREATE TABLE IF NOT EXISTS sandbox_execution_resource_usage
(
    id            VARCHAR(32) PRIMARY KEY,
    task_id       VARCHAR(32) NOT NULL,
    wall_millis   BIGINT,
    cpu_millis    BIGINT,
    max_rss_bytes BIGINT,
    output_bytes  BIGINT,
    exit_code     INTEGER,
    reported_at   BIGINT      NOT NULL,
    created_at    BIGINT,
    updated_at    BIGINT,
    sort_num      INTEGER     NOT NULL DEFAULT 0,
    deleted       BOOLEAN     NOT NULL DEFAULT FALSE,
    state         INTEGER     NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS sandbox_execution_resource_usage_uk_task
    ON sandbox_execution_resource_usage(task_id) WHERE deleted = FALSE;
