CREATE TABLE IF NOT EXISTS agent_skill_execution_config
(
    id                VARCHAR(32) PRIMARY KEY,
    skill_version_id  VARCHAR(32) NOT NULL,
    enabled           BOOLEAN     NOT NULL DEFAULT FALSE,
    entry_resource_id VARCHAR(32),
    runtime           VARCHAR(16),
    output_formats    TEXT,
    timeout_seconds   INTEGER     NOT NULL DEFAULT 60,
    max_output_files  INTEGER     NOT NULL DEFAULT 3,
    max_output_bytes  BIGINT      NOT NULL DEFAULT 52428800,
    created_at        BIGINT,
    updated_at        BIGINT,
    sort_num          INTEGER     NOT NULL DEFAULT 0,
    deleted           BOOLEAN     NOT NULL DEFAULT FALSE,
    state             INTEGER     NOT NULL DEFAULT 0
);
COMMENT ON TABLE agent_skill_execution_config IS 'Skill 版本的声明式产物执行策略；不包含命令、镜像、挂载或网络配置';
CREATE UNIQUE INDEX IF NOT EXISTS agent_skill_execution_config_uk ON agent_skill_execution_config(skill_version_id) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS agent_sandbox_execution
(
    id                        VARCHAR(32) PRIMARY KEY,
    run_id                    VARCHAR(64) NOT NULL,
    skill_version_id          VARCHAR(32) NOT NULL,
    message_id                VARCHAR(32),
    user_id                   VARCHAR(32) NOT NULL,
    agent_definition_id       VARCHAR(32) NOT NULL,
    execution_config_snapshot TEXT        NOT NULL,
    resource_snapshot         TEXT        NOT NULL,
    input_json                TEXT        NOT NULL,
    token_hash                VARCHAR(64) NOT NULL,
    status                    SMALLINT    NOT NULL DEFAULT 0,
    expires_at                BIGINT      NOT NULL,
    started_at                BIGINT,
    completed_at              BIGINT,
    log_summary               TEXT,
    failure_reason            TEXT,
    created_at                BIGINT,
    updated_at                BIGINT,
    sort_num                  INTEGER     NOT NULL DEFAULT 0,
    deleted                   BOOLEAN     NOT NULL DEFAULT FALSE,
    state                     INTEGER     NOT NULL DEFAULT 0
);
COMMENT ON TABLE agent_sandbox_execution IS '一次性冻结的 Sandbox 执行任务；状态 0-待领取、1-运行中、2-成功、3-失败、4-过期';
CREATE UNIQUE INDEX IF NOT EXISTS agent_sandbox_execution_uk_run ON agent_sandbox_execution(run_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_sandbox_execution_idx_version ON agent_sandbox_execution(skill_version_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_artifact
(
    id               VARCHAR(32) PRIMARY KEY,
    execution_id     VARCHAR(32)   NOT NULL,
    run_id           VARCHAR(64)   NOT NULL,
    skill_version_id VARCHAR(32)   NOT NULL,
    message_id       VARCHAR(32),
    file_name        VARCHAR(255)  NOT NULL,
    object_key       VARCHAR(1024) NOT NULL,
    content_sha256   VARCHAR(64)   NOT NULL,
    content_type     VARCHAR(128)  NOT NULL,
    size             BIGINT        NOT NULL,
    expires_at       BIGINT,
    log_summary      TEXT,
    status           SMALLINT      NOT NULL DEFAULT 1,
    created_at       BIGINT,
    updated_at       BIGINT,
    sort_num         INTEGER       NOT NULL DEFAULT 0,
    deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    state            INTEGER       NOT NULL DEFAULT 0
);
COMMENT ON TABLE agent_artifact IS 'Sandbox 校验后的可下载产物；聊天附件只引用此记录对应的对象';
CREATE INDEX IF NOT EXISTS agent_artifact_idx_execution ON agent_artifact(execution_id, created_at DESC);
CREATE INDEX IF NOT EXISTS agent_artifact_idx_message ON agent_artifact(message_id, created_at DESC);
