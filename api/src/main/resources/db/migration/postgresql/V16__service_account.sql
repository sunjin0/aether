-- 业务服务账号：凭据仅保存 BCrypt 哈希，版本号用于立即吊销已签发 JWT。
CREATE TABLE IF NOT EXISTS sys_service_account
(
    id            VARCHAR(32) PRIMARY KEY,
    user_id       VARCHAR(32)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    description   VARCHAR(1024),
    client_id     VARCHAR(64)  NOT NULL,
    secret_hash   VARCHAR(255) NOT NULL,
    token_version INTEGER      NOT NULL DEFAULT 1,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_used_at  BIGINT,
    created_at    BIGINT,
    updated_at    BIGINT,
    sort_num      INTEGER      NOT NULL DEFAULT 0,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    state         INTEGER      NOT NULL DEFAULT 0,
    UNIQUE (client_id),
    UNIQUE (user_id)
);
CREATE INDEX IF NOT EXISTS sys_service_account_idx_enabled ON sys_service_account(enabled) WHERE deleted = FALSE;
