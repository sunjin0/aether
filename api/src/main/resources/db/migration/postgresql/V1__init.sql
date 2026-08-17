-- Aether PostgreSQL bootstrap
-- Consolidated from the entity model and historical schema/data migrations.
-- This is the only SQL entry point: it creates a complete database and loads seed data.

-- ============================================================================
-- Consolidated source: V1__schema.sql
-- ============================================================================
-- Aether PostgreSQL schema.
-- Contains table definitions, indexes and pgvector infrastructure only.
-- Flyway V1 baseline schema for a new PostgreSQL database.


-- Source: api/src/main/resources/sql/postgresql/parts/001-system.sql

-- PostgreSQL 16 schema generated from the current MySQL initialization scripts.
-- Apply 001, 002, then 003 only for an empty database. Production data migration uses pgloader.

/*
 Navicat Premium Data Transfer

 Source Server         : 鏈湴鏁版嵁搴? Source Server Type    : MySQL
 Source Server Version : 80036
 Source Host           : localhost:3306
 Source Schema         : demo

 Target Server Type    : MySQL
 Target Server Version : 80036
 File Encoding         : 65001

 Date: 22/07/2025 17:15:36
*/

-- ----------------------------
-- Table structure for msg_email
-- ----------------------------
CREATE TABLE msg_email (
                              id VARCHAR(32) NOT NULL,
                              user_id VARCHAR(32) NOT NULL,
                              email varchar(255) NOT NULL,
                              type varchar(255) NOT NULL,
                              code INTEGER NULL,
                              subject varchar(255) NOT NULL,
                              body text NOT NULL,
                              state INTEGER NOT NULL DEFAULT 0,
                              created_at bigint NOT NULL,
                              updated_at bigint NOT NULL,
                              deleted BOOLEAN NOT NULL DEFAULT FALSE,
                              sort_num INTEGER NOT NULL DEFAULT 1,
                              PRIMARY KEY (id)

);

-- ----------------------------
-- Records of msg_email
-- ----------------------------

-- ----------------------------
-- Table structure for msg_sms
-- ----------------------------
CREATE TABLE msg_sms (
                            id VARCHAR(32) NOT NULL,
                            user_id VARCHAR(32) NOT NULL,
                            phone varchar(255) NOT NULL,
                            code INTEGER NOT NULL,
                            type varchar(255) NOT NULL,
                            subject varchar(255) NOT NULL,
                            body text NOT NULL,
                            state INTEGER NOT NULL DEFAULT 0,
                            deleted BOOLEAN NULL DEFAULT FALSE,
                            created_at BIGINT NOT NULL,
                            updated_at BIGINT NOT NULL,
                            sort_num INTEGER NOT NULL DEFAULT 1,
                            PRIMARY KEY (id)

);

-- ----------------------------
-- Records of msg_sms
-- ----------------------------

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
CREATE TABLE sys_config (
                               id VARCHAR(32) NOT NULL,
                               code varchar(255) NOT NULL,
                               name varchar(255) NOT NULL,
                               parent varchar(255) NULL,
                               value varchar(255) NOT NULL,
                               remark varchar(255) NOT NULL,
                               state INTEGER NOT NULL DEFAULT 0,
                               deleted BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at bigint NOT NULL,
                               updated_at bigint NOT NULL,
                               sort_num INTEGER NOT NULL DEFAULT 1,
                               PRIMARY KEY (id)

);

-- ----------------------------
-- Records of sys_config
-- ----------------------------

-- ----------------------------
-- Table structure for sys_dict
-- ----------------------------
CREATE TABLE sys_dict (
                             id VARCHAR(32) NOT NULL,
                             code varchar(255) NOT NULL,
                             parent varchar(255) NULL,
                             name varchar(255) NOT NULL,
                             name_cn varchar(255) NOT NULL,
                             val varchar(255) NULL,
                             remark varchar(255) NULL,
                             state INTEGER NOT NULL DEFAULT 0,
                             deleted BOOLEAN NOT NULL DEFAULT FALSE,
                             created_at bigint NOT NULL,
                             updated_at bigint NOT NULL,
                             sort_num INTEGER NOT NULL DEFAULT 1,
                             PRIMARY KEY (id)

);

-- ----------------------------
-- Records of sys_dict
-- ----------------------------

-- ----------------------------
-- Table structure for sys_resource
-- ----------------------------
CREATE TABLE sys_resource (
                                 id VARCHAR(32) NOT NULL,
                                 name varchar(255) NOT NULL,
                                 name_cn varchar(255) NOT NULL,
                                 path varchar(255) NULL,
                                 type varchar(255) NOT NULL,
                                 icon varchar(255) NULL,
                                 parent_id VARCHAR(32) NOT NULL,
                                 leaf BOOLEAN NULL DEFAULT FALSE,
                                 description varchar(255) NULL,
                                 state INTEGER NOT NULL DEFAULT 0,
                                 deleted BOOLEAN NOT NULL DEFAULT FALSE,
                                 created_at bigint NOT NULL,
                                 updated_at bigint NOT NULL,
                                 sort_num INTEGER NOT NULL DEFAULT 1,
                                 PRIMARY KEY (id)

);

-- ----------------------------
-- Records of sys_resource
-- ----------------------------

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
CREATE TABLE sys_role (
                             id VARCHAR(32) NOT NULL,
                             name varchar(255) NOT NULL,
                             description varchar(255) NULL,
                             state INTEGER NOT NULL DEFAULT 0,
                             deleted BOOLEAN NULL DEFAULT FALSE,
                             created_at bigint NULL,
                             updated_at bigint NULL,
                             sort_num INTEGER NULL DEFAULT 1,
                             PRIMARY KEY (id)

);

-- ----------------------------
-- Records of sys_role
-- ----------------------------

-- ----------------------------
-- Table structure for sys_role_resource
-- ----------------------------
CREATE TABLE sys_role_resource (
                                      id VARCHAR(32) NOT NULL,
                                      role_id VARCHAR(32) NOT NULL,
                                      resource_id VARCHAR(32) NOT NULL,
                                      state INTEGER NOT NULL DEFAULT 0,
                                      deleted BOOLEAN NOT NULL DEFAULT FALSE,
                                      created_at bigint NOT NULL,
                                      updated_at bigint NOT NULL,
                                      sort_num INTEGER NOT NULL DEFAULT 1,
                                      PRIMARY KEY (id)

);

-- ----------------------------
-- Records of sys_role_resource
-- ----------------------------

-- ----------------------------
-- Table structure for sys_token
-- ----------------------------
CREATE TABLE sys_token (
                              id VARCHAR(32) NOT NULL,
                              user_id VARCHAR(32) NOT NULL,
                              token varchar(512) NOT NULL,
                              refresh_token varchar(512) NOT NULL,
                              state INTEGER NOT NULL DEFAULT 0,
                              deleted BOOLEAN NOT NULL DEFAULT FALSE,
                              created_at bigint NOT NULL,
                              updated_at bigint NOT NULL,
                              sort_num INTEGER NOT NULL DEFAULT 1,
                              PRIMARY KEY (id)

);

-- ----------------------------
-- Records of sys_token
-- ----------------------------

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
CREATE TABLE sys_user (
                             id VARCHAR(32) NOT NULL,
                             username varchar(255) NOT NULL,
                             sex varchar(255) NULL,
                             type varchar(255) NULL,
                             email varchar(255) NULL,
                             phone varchar(255) NULL,
                             avatar varchar(255) NULL,
                             password varchar(255) NOT NULL,
                             state INTEGER NOT NULL DEFAULT 0,
                             deleted BOOLEAN NULL DEFAULT FALSE,
                             created_at bigint NULL,
                             updated_at bigint NULL,
                             sort_num INTEGER NULL DEFAULT 1,
                             PRIMARY KEY (id)

);

-- ----------------------------
-- Records of sys_user
-- ----------------------------

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
CREATE TABLE sys_user_role (
                                  id VARCHAR(32) NOT NULL,
                                  user_id VARCHAR(32) NOT NULL,
                                  role_id VARCHAR(32) NOT NULL,
                                  state INTEGER NOT NULL DEFAULT 0,
                                  deleted BOOLEAN NULL DEFAULT FALSE,
                                  created_at bigint NULL,
                                  updated_at bigint NULL,
                                  sort_num INTEGER NULL DEFAULT 1,
                                  PRIMARY KEY (id)

);

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------

/*
 * Copyright (c) 2026. Lorem ipsum dolor sit amet, consectetur adipiscing elit.
 * Morbi non lorem porttitor neque feugiat blandit. Ut vitae ipsum eget quam lacinia accumsan.
 * Etiam sed turpis ac ipsum condimentum fringilla. Maecenas magna.
 * Proin dapibus sapien vel ante. Aliquam erat volutpat. Pellentesque sagittis ligula eget metus.
 * Vestibulum commodo. Ut rhoncus gravida arcu.
 */

-- ----------------------------
-- Table structure for user_member
-- ----------------------------
CREATE TABLE user_member (
                                id VARCHAR(32) NOT NULL,
                                username varchar(255) NULL,
                                password varchar(255) NULL,
                                nickname varchar(255) NULL,
                                phone varchar(255) NULL,
                                email varchar(255) NULL,
                                state INTEGER NOT NULL DEFAULT 0,
                                deleted BOOLEAN NULL DEFAULT FALSE,
                                created_at bigint NULL,
                                updated_at bigint NULL,
                                sort_num INTEGER NULL DEFAULT 1,
                                PRIMARY KEY (id)

);

-- ----------------------------
-- Records of user_member
-- ----------------------------

CREATE SEQUENCE IF NOT EXISTS sys_dict_id_seq;

-- Source: api/src/main/resources/sql/postgresql/parts/002-agent-platform.sql

-- PostgreSQL 16 schema generated from the current MySQL initialization scripts.
-- Apply 001, 002, then 003 only for an empty database. Production data migration uses pgloader.

-- agent-platform V0.2 寤鸿〃鑴氭湰
-- 琛ㄥ墠缂€锛歛gent_
-- 鎵€鏈夎〃缁ф壙 BaseEntity 鍏叡瀛楁锛歩d, created_at, updated_at, sort_num, deleted, state

-- =====================================================
-- 1. agent_model_provider锛堟ā鍨嬩緵搴斿晢锛?-- =====================================================
CREATE TABLE IF NOT EXISTS agent_model_provider (
    id VARCHAR(32)       NOT NULL PRIMARY KEY,
    name            VARCHAR(64)  NOT NULL,
    type            VARCHAR(32)  NOT NULL,
    api_base_url    VARCHAR(256),
    api_key         VARCHAR(512),
    default_model   VARCHAR(64),
    context_window  INTEGER          NOT NULL DEFAULT 32768,
    status          SMALLINT      NOT NULL DEFAULT 1,
    sort            INTEGER          NOT NULL DEFAULT 0,
    remark          VARCHAR(512),
    created_at      BIGINT,
    updated_at      BIGINT,
    sort_num        INTEGER          NOT NULL DEFAULT 0,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    state           INTEGER          NOT NULL DEFAULT 0
);

-- 2. agent_definition锛圓gent瀹氫箟锛?-- =====================================================
CREATE TABLE IF NOT EXISTS agent_definition (
    id VARCHAR(32)       NOT NULL PRIMARY KEY,
    name                VARCHAR(64)  NOT NULL,
    code                VARCHAR(64)  NOT NULL,
    description         VARCHAR(512),
    system_prompt       TEXT,
    model_provider_id VARCHAR(32),
    model               VARCHAR(64),
    temperature         NUMERIC(3,2) NOT NULL DEFAULT 0.70,
    max_tokens          INTEGER          NOT NULL DEFAULT 2048,
    status              SMALLINT      NOT NULL DEFAULT 0,
    max_tool_rounds     INTEGER          NOT NULL DEFAULT 1,
    default_thinking    BOOLEAN    NOT NULL DEFAULT FALSE,
    default_reasoning_effort VARCHAR(16),
    access_type         VARCHAR(16)  NOT NULL DEFAULT 'private',
    sort                INTEGER          NOT NULL DEFAULT 0,
    remark              VARCHAR(512),
    created_at          BIGINT,
    updated_at          BIGINT,
    sort_num            INTEGER          NOT NULL DEFAULT 0,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    state               INTEGER          NOT NULL DEFAULT 0
);

-- =====================================================
-- 3. agent_mcp_server锛圡CP鏈嶅姟锛?-- =====================================================
CREATE TABLE IF NOT EXISTS agent_mcp_server (
    id VARCHAR(32)       NOT NULL PRIMARY KEY,
    name                  VARCHAR(64)  NOT NULL,
    code                  VARCHAR(64)  NOT NULL,
    transport             VARCHAR(32)  NOT NULL DEFAULT 'http',
    base_url              VARCHAR(512),
    request_headers       TEXT,
    auth_type             VARCHAR(32)  NOT NULL DEFAULT 'none',
    auth_token            VARCHAR(1024),
    command               VARCHAR(512),
    args                  TEXT,
    timeout_ms            INTEGER          NOT NULL DEFAULT 30000,
    status                SMALLINT      NOT NULL DEFAULT 1,
    remark                VARCHAR(512),
    created_at            BIGINT,
    updated_at            BIGINT,
    sort_num              INTEGER          NOT NULL DEFAULT 0,
    deleted               BOOLEAN      NOT NULL DEFAULT FALSE,
    state                 INTEGER          NOT NULL DEFAULT 0
);

-- =====================================================
-- 4. agent_tool锛堝伐鍏凤級
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_tool (
    id VARCHAR(32)       NOT NULL PRIMARY KEY,
    name                  VARCHAR(64)  NOT NULL,
    code                  VARCHAR(64)  NOT NULL,
    description           VARCHAR(512),
    tool_type             VARCHAR(64),
    mcp_server_id VARCHAR(32)       NOT NULL,
    mcp_tool_name         VARCHAR(128),
    mcp_input_schema      TEXT,
    timeout_ms            INTEGER          NOT NULL DEFAULT 30000,
    status                SMALLINT      NOT NULL DEFAULT 1,
    remark                VARCHAR(512),
    created_at            BIGINT,
    updated_at            BIGINT,
    sort_num              INTEGER          NOT NULL DEFAULT 0,
    deleted               BOOLEAN      NOT NULL DEFAULT FALSE,
    state                 INTEGER          NOT NULL DEFAULT 0
);

-- =====================================================
-- 5. agent_tool_binding锛堝伐鍏风粦瀹氾級
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_tool_binding (
    id VARCHAR(32)       NOT NULL PRIMARY KEY,
    agent_definition_id VARCHAR(32)       NOT NULL,
    tool_id VARCHAR(32)       NOT NULL,
    priority              INTEGER          NOT NULL DEFAULT 0,
    status                SMALLINT      NOT NULL DEFAULT 1,
    created_at            BIGINT,
    updated_at            BIGINT,
    sort_num              INTEGER          NOT NULL DEFAULT 0,
    deleted               BOOLEAN      NOT NULL DEFAULT FALSE,
    state                 INTEGER          NOT NULL DEFAULT 0
);

-- =====================================================
-- 6. agent_conversation锛堜細璇濓級
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_conversation (
    id VARCHAR(32)       NOT NULL PRIMARY KEY,
    user_id VARCHAR(32)       NOT NULL,
    agent_definition_id VARCHAR(32)       NOT NULL,
    title               VARCHAR(256),
    message_count       INTEGER          NOT NULL DEFAULT 0,
    status              SMALLINT      NOT NULL DEFAULT 0,
    summary             TEXT,
    summary_covered_message_id VARCHAR(32),
    summary_covered_created_at BIGINT,
    summary_updated_at  BIGINT,
    created_at          BIGINT,
    updated_at          BIGINT,
    sort_num            INTEGER          NOT NULL DEFAULT 0,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    state               INTEGER          NOT NULL DEFAULT 0
);

-- =====================================================
-- 7. agent_message锛堟秷鎭級
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_message (
    id VARCHAR(32)       NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(32)       NOT NULL,
    role              VARCHAR(16)  NOT NULL,
    message_type      VARCHAR(32)  DEFAULT 'chat',
    interaction_type  VARCHAR(32),
    interaction_status VARCHAR(32),
    question_config   TEXT,
    parent_message_id VARCHAR(64),
    answered_at       BIGINT,
    expires_at        BIGINT,
    content           TEXT,
    reasoning_content TEXT,
    tool_calls        TEXT,
    tool_call_id      VARCHAR(64),
    tool_result       TEXT,
    model             VARCHAR(64),
    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    total_tokens      INTEGER,
    reasoning_tokens  INTEGER,
    latency_ms        INTEGER,
    edited            SMALLINT      NOT NULL DEFAULT 0,
    original_content  TEXT,
    edited_at         BIGINT,
    created_at        BIGINT,
    updated_at        BIGINT,
    sort_num          INTEGER          NOT NULL DEFAULT 0,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    state             INTEGER          NOT NULL DEFAULT 0
);

-- =====================================================
-- 8. agent_run锛堣繍琛岃褰曪級
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_run (
    id VARCHAR(32)       NOT NULL PRIMARY KEY,
    agent_definition_id VARCHAR(32)       NOT NULL,
    user_id VARCHAR(32)       NOT NULL,
    conversation_id VARCHAR(32),
    message_id VARCHAR(32),
    input_content       TEXT,
    output_content      TEXT,
    model               VARCHAR(64),
    model_provider_id VARCHAR(32),
    prompt_tokens       INTEGER,
    completion_tokens   INTEGER,
    total_tokens        INTEGER,
    latency_ms          INTEGER,
    status              SMALLINT      NOT NULL DEFAULT 0,
    error_msg           VARCHAR(1024),
    created_at          BIGINT,
    updated_at          BIGINT,
    sort_num            INTEGER          NOT NULL DEFAULT 0,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    state               INTEGER          NOT NULL DEFAULT 0
);

-- =====================================================
-- 9. agent_tool_call_log锛堝伐鍏疯皟鐢ㄦ棩蹇楋級
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_tool_call_log (
    id VARCHAR(32)       NOT NULL PRIMARY KEY,
    run_id VARCHAR(32)       NOT NULL,
    tool_id VARCHAR(32),
    tool_call_id        VARCHAR(128),
    tool_name           VARCHAR(128),
    arguments           TEXT,
    agent_definition_id VARCHAR(32)       NOT NULL,
    request_url         VARCHAR(512),
    request_method      VARCHAR(16),
    request_headers     TEXT,
    request_body        TEXT,
    response_status     INTEGER,
    response_body       TEXT,
    latency_ms          INTEGER,
    status              SMALLINT      NOT NULL DEFAULT 0,
    error_msg           VARCHAR(1024),
    created_at          BIGINT,
    updated_at          BIGINT,
    sort_num            INTEGER          NOT NULL DEFAULT 0,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    state               INTEGER          NOT NULL DEFAULT 0
);

-- =====================================================
-- 10. agent_workflow锛堝伐浣滄祦 鈥?V0.7棰勭暀锛?-- =====================================================
CREATE TABLE IF NOT EXISTS agent_workflow (
    id VARCHAR(32)       NOT NULL PRIMARY KEY,
    agent_definition_id VARCHAR(32)       NOT NULL,
    name                VARCHAR(64)  NOT NULL,
    description         VARCHAR(512),
    nodes               TEXT,
    edges               TEXT,
    status              SMALLINT      NOT NULL DEFAULT 0,
    created_at          BIGINT,
    updated_at          BIGINT,
    sort_num            INTEGER          NOT NULL DEFAULT 0,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    state               INTEGER          NOT NULL DEFAULT 0
);

-- =====================================================
-- 11. knowledge_base锛堢煡璇嗗簱 鈥?V0.7棰勭暀锛?-- =====================================================
CREATE TABLE IF NOT EXISTS knowledge_base (
    id VARCHAR(32)       NOT NULL PRIMARY KEY,
    scope               VARCHAR(16)  NOT NULL DEFAULT 'PLATFORM',
    embedding_provider_id VARCHAR(32),
    name                VARCHAR(64)  NOT NULL,
    description         VARCHAR(512),
    index_status        SMALLINT      NOT NULL DEFAULT 0,
    status              SMALLINT      NOT NULL DEFAULT 1,
    created_at          BIGINT,
    updated_at          BIGINT,
    sort_num            INTEGER          NOT NULL DEFAULT 0,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    state               INTEGER          NOT NULL DEFAULT 0
);

-- =====================================================
-- 11.1 agent_knowledge_base_binding（Agent 与知识库绑定）
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_knowledge_base_binding (
    id VARCHAR(32)       NOT NULL PRIMARY KEY,
    agent_definition_id  VARCHAR(32) NOT NULL,
    knowledge_base_id    VARCHAR(32) NOT NULL,
    status               SMALLINT    NOT NULL DEFAULT 1,
    created_at           BIGINT,
    updated_at           BIGINT,
    sort_num             INTEGER     NOT NULL DEFAULT 0,
    deleted              BOOLEAN     NOT NULL DEFAULT FALSE,
    state                INTEGER     NOT NULL DEFAULT 0
);

-- =====================================================
-- 12. knowledge_document锛堟枃妗?鈥?V0.7棰勭暀锛?-- =====================================================
CREATE TABLE IF NOT EXISTS knowledge_document (
    id VARCHAR(32)       NOT NULL PRIMARY KEY,
    knowledge_base_id VARCHAR(32)       NOT NULL,
    title             VARCHAR(256),
    content           TEXT,
    source_url        VARCHAR(512),
    chunk_count       INTEGER,
    status            SMALLINT      NOT NULL DEFAULT 0,
    created_at        BIGINT,
    updated_at        BIGINT,
    sort_num          INTEGER          NOT NULL DEFAULT 0,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    state             INTEGER          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS agent_model_provider_uk_name ON agent_model_provider (name) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS agent_definition_uk_code ON agent_definition (code) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS agent_mcp_server_uk_code ON agent_mcp_server (code) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS agent_tool_uk_code ON agent_tool (code) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS agent_tool_uk_server_tool ON agent_tool (mcp_server_id, mcp_tool_name) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS agent_tool_binding_uk_agent_tool ON agent_tool_binding (agent_definition_id, tool_id) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS agent_model_provider_idx_type ON agent_model_provider (type);
CREATE INDEX IF NOT EXISTS agent_model_provider_idx_status ON agent_model_provider (state);
CREATE INDEX IF NOT EXISTS agent_definition_idx_name ON agent_definition (name);
CREATE INDEX IF NOT EXISTS agent_definition_idx_model_provider_id ON agent_definition (model_provider_id);
CREATE INDEX IF NOT EXISTS agent_definition_idx_status ON agent_definition (state);
CREATE INDEX IF NOT EXISTS agent_mcp_server_idx_name ON agent_mcp_server (name);
CREATE INDEX IF NOT EXISTS agent_mcp_server_idx_status ON agent_mcp_server (status);
CREATE INDEX IF NOT EXISTS agent_tool_idx_name ON agent_tool (name);
CREATE INDEX IF NOT EXISTS agent_tool_idx_tool_type ON agent_tool (tool_type);
CREATE INDEX IF NOT EXISTS agent_tool_idx_mcp_server_id ON agent_tool (mcp_server_id);
CREATE INDEX IF NOT EXISTS agent_tool_idx_status ON agent_tool (status);
CREATE INDEX IF NOT EXISTS agent_tool_binding_idx_agent_id ON agent_tool_binding (agent_definition_id);
CREATE INDEX IF NOT EXISTS agent_tool_binding_idx_tool_id ON agent_tool_binding (tool_id);
CREATE INDEX IF NOT EXISTS agent_conversation_idx_user_agent ON agent_conversation (user_id, agent_definition_id);
CREATE INDEX IF NOT EXISTS agent_conversation_idx_user_id ON agent_conversation (user_id);
CREATE INDEX IF NOT EXISTS agent_conversation_idx_agent_id ON agent_conversation (agent_definition_id);
CREATE INDEX IF NOT EXISTS agent_conversation_idx_status ON agent_conversation (state);
CREATE INDEX IF NOT EXISTS agent_message_idx_conversation_id ON agent_message (conversation_id);
CREATE INDEX IF NOT EXISTS agent_message_idx_conversation_history ON agent_message (conversation_id, deleted, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS agent_message_idx_role ON agent_message (role);
CREATE INDEX IF NOT EXISTS agent_message_idx_parent_message_id ON agent_message (parent_message_id);
CREATE INDEX IF NOT EXISTS agent_message_idx_interaction_status ON agent_message (conversation_id, interaction_status, deleted);
CREATE INDEX IF NOT EXISTS agent_message_idx_create_time ON agent_message (created_at);
CREATE INDEX IF NOT EXISTS agent_run_idx_agent_id ON agent_run (agent_definition_id);
CREATE INDEX IF NOT EXISTS agent_run_idx_user_id ON agent_run (user_id);
CREATE INDEX IF NOT EXISTS agent_run_idx_conversation_id ON agent_run (conversation_id);
CREATE INDEX IF NOT EXISTS agent_run_idx_status ON agent_run (state);
CREATE INDEX IF NOT EXISTS agent_run_idx_create_time ON agent_run (created_at);
CREATE INDEX IF NOT EXISTS agent_tool_call_log_idx_run_id ON agent_tool_call_log (run_id);
CREATE INDEX IF NOT EXISTS agent_tool_call_log_idx_tool_id ON agent_tool_call_log (tool_id);
CREATE INDEX IF NOT EXISTS agent_tool_call_log_idx_agent_id ON agent_tool_call_log (agent_definition_id);
CREATE INDEX IF NOT EXISTS agent_tool_call_log_idx_status ON agent_tool_call_log (state);
CREATE INDEX IF NOT EXISTS agent_tool_call_log_idx_create_time ON agent_tool_call_log (created_at);
CREATE INDEX IF NOT EXISTS agent_tool_call_log_idx_tool_call_log_run_call ON agent_tool_call_log (run_id, tool_call_id);
CREATE INDEX IF NOT EXISTS agent_workflow_idx_agent_id ON agent_workflow (agent_definition_id);
CREATE INDEX IF NOT EXISTS agent_workflow_idx_status ON agent_workflow (state);
CREATE INDEX IF NOT EXISTS knowledge_base_idx_scope ON knowledge_base (scope);
CREATE INDEX IF NOT EXISTS knowledge_base_idx_embedding_provider_id ON knowledge_base (embedding_provider_id);
CREATE INDEX IF NOT EXISTS knowledge_base_idx_status ON knowledge_base (state);
CREATE UNIQUE INDEX IF NOT EXISTS agent_knowledge_base_binding_uk_agent_kb ON agent_knowledge_base_binding (agent_definition_id, knowledge_base_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_knowledge_base_binding_idx_agent_id ON agent_knowledge_base_binding (agent_definition_id);
CREATE INDEX IF NOT EXISTS agent_knowledge_base_binding_idx_kb_id ON agent_knowledge_base_binding (knowledge_base_id);
CREATE INDEX IF NOT EXISTS agent_knowledge_base_binding_idx_status ON agent_knowledge_base_binding (status);
CREATE INDEX IF NOT EXISTS knowledge_document_idx_knowledge_base_id ON knowledge_document (knowledge_base_id);
CREATE INDEX IF NOT EXISTS knowledge_document_idx_status ON knowledge_document (state);

-- Source: api/src/main/resources/sql/postgresql/parts/005-pgvector.sql

-- pgvector foundation for the future knowledge-base retrieval capability.
-- The OpenAI-compatible text-embedding-3-small model uses 1536 dimensions.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_document_chunk (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    knowledge_base_id VARCHAR(32) NOT NULL,
    document_id VARCHAR(32) NOT NULL,
    document_version_id VARCHAR(32),
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER NOT NULL DEFAULT 0,
    embedding vector(1536) NOT NULL,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_chunk_knowledge_base
    ON knowledge_document_chunk (knowledge_base_id, deleted);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_chunk_document
    ON knowledge_document_chunk (document_id, deleted, chunk_index);
-- 仅限制活动分块唯一；历史逻辑删除分块允许保留多代索引记录。
CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_document_chunk_version_active
    ON knowledge_document_chunk (document_version_id, chunk_index) WHERE deleted = FALSE AND document_version_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_knowledge_document_chunk_embedding_cosine
    ON knowledge_document_chunk USING hnsw (embedding vector_cosine_ops);

-- Enterprise knowledge-base extension.
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS owner_admin_id VARCHAR(32);
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS visibility VARCHAR(16) NOT NULL DEFAULT 'platform';
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS retrieval_config TEXT;
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS review_config TEXT;
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS reference_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS last_referenced_at BIGINT;
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) NOT NULL DEFAULT 'text';
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS original_file_name VARCHAR(512);
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS file_extension VARCHAR(32);
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS mime_type VARCHAR(128);
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS file_size BIGINT;
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS file_checksum VARCHAR(128);
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS storage_bucket VARCHAR(128);
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS storage_object_key VARCHAR(1024);
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS current_version_no INTEGER NOT NULL DEFAULT 0;
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS draft_version_id VARCHAR(32);
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS submitted_version_id VARCHAR(32);
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS review_status VARCHAR(24) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS review_updated_at BIGINT;
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS index_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS parser_type VARCHAR(64);
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS index_error_message TEXT;
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS indexed_at BIGINT;
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS reference_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS last_referenced_at BIGINT;
ALTER TABLE knowledge_document_chunk ADD COLUMN IF NOT EXISTS document_version_id VARCHAR(32);
ALTER TABLE knowledge_document_chunk ADD COLUMN IF NOT EXISTS page_no INTEGER;
ALTER TABLE knowledge_document_chunk ADD COLUMN IF NOT EXISTS section_path VARCHAR(512);
ALTER TABLE knowledge_document_chunk ADD COLUMN IF NOT EXISTS content_hash VARCHAR(128);
ALTER TABLE knowledge_document_chunk ADD COLUMN IF NOT EXISTS metadata TEXT;
ALTER TABLE knowledge_document_chunk ADD COLUMN IF NOT EXISTS reference_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE knowledge_document_chunk ADD COLUMN IF NOT EXISTS last_referenced_at BIGINT;
ALTER TABLE agent_message ADD COLUMN IF NOT EXISTS citations TEXT;
CREATE TABLE IF NOT EXISTS knowledge_document_version (id VARCHAR(32) PRIMARY KEY, knowledge_document_id VARCHAR(32) NOT NULL, version_no INTEGER NOT NULL, content TEXT, storage_bucket VARCHAR(128), storage_object_key VARCHAR(1024), file_checksum VARCHAR(128), parser_type VARCHAR(64), index_status SMALLINT NOT NULL DEFAULT 0, index_error_message TEXT, indexed_at BIGINT, chunk_count INTEGER NOT NULL DEFAULT 0, created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0, CONSTRAINT uk_knowledge_document_version UNIQUE (knowledge_document_id, version_no, deleted));
ALTER TABLE knowledge_document_version ADD COLUMN IF NOT EXISTS original_content TEXT;
ALTER TABLE knowledge_document_version ADD COLUMN IF NOT EXISTS structured_content TEXT;
ALTER TABLE knowledge_document_version ADD COLUMN IF NOT EXISTS content_checksum VARCHAR(128);
ALTER TABLE knowledge_document_version ADD COLUMN IF NOT EXISTS review_status VARCHAR(24) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE knowledge_document_version ADD COLUMN IF NOT EXISTS source_version_id VARCHAR(32);
ALTER TABLE knowledge_document_version ADD COLUMN IF NOT EXISTS submitted_by VARCHAR(32);
ALTER TABLE knowledge_document_version ADD COLUMN IF NOT EXISTS submitted_at BIGINT;
ALTER TABLE knowledge_document_version ADD COLUMN IF NOT EXISTS reviewed_by VARCHAR(32);
ALTER TABLE knowledge_document_version ADD COLUMN IF NOT EXISTS reviewed_at BIGINT;
ALTER TABLE knowledge_document_version ADD COLUMN IF NOT EXISTS review_comment TEXT;
CREATE TABLE IF NOT EXISTS knowledge_index_job (id VARCHAR(32) PRIMARY KEY, knowledge_base_id VARCHAR(32) NOT NULL, document_id VARCHAR(32) NOT NULL, document_version_id VARCHAR(32) NOT NULL, job_type VARCHAR(32) NOT NULL, status VARCHAR(16) NOT NULL, retry_count INTEGER NOT NULL DEFAULT 0, max_retry_count INTEGER NOT NULL DEFAULT 3, error_message TEXT, statistics TEXT, started_at BIGINT, finished_at BIGINT, created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS knowledge_review_task (id VARCHAR(32) PRIMARY KEY, knowledge_base_id VARCHAR(32) NOT NULL, document_id VARCHAR(32) NOT NULL, document_version_id VARCHAR(32) NOT NULL, submitter_id VARCHAR(32) NOT NULL, reviewer_id VARCHAR(32), status VARCHAR(16) NOT NULL, source_checksum VARCHAR(128) NOT NULL, submit_comment TEXT, review_comment TEXT, submitted_at BIGINT NOT NULL, claimed_at BIGINT, reviewed_at BIGINT, created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS knowledge_review_action_log (id VARCHAR(32) PRIMARY KEY, review_task_id VARCHAR(32), document_id VARCHAR(32) NOT NULL, document_version_id VARCHAR(32) NOT NULL, operator_id VARCHAR(32), action VARCHAR(32) NOT NULL, before_status VARCHAR(24), after_status VARCHAR(24), comment TEXT, metadata TEXT, created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS knowledge_ai_review (id VARCHAR(32) PRIMARY KEY, knowledge_base_id VARCHAR(32) NOT NULL, document_id VARCHAR(32) NOT NULL, document_version_id VARCHAR(32) NOT NULL, source_checksum VARCHAR(128) NOT NULL, source_content TEXT, model_provider_id VARCHAR(32), model VARCHAR(128), prompt_version VARCHAR(32), status VARCHAR(16) NOT NULL, score INTEGER, summary TEXT, issues TEXT, statistics TEXT, error_message TEXT, started_at BIGINT, finished_at BIGINT, created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS knowledge_ai_review_issue (id VARCHAR(32) PRIMARY KEY, ai_review_id VARCHAR(32) NOT NULL, document_version_id VARCHAR(32) NOT NULL, block_id VARCHAR(64), issue_type VARCHAR(32) NOT NULL, severity VARCHAR(16) NOT NULL, message TEXT NOT NULL, original_excerpt TEXT, suggested_patch TEXT, handle_status VARCHAR(16) NOT NULL DEFAULT 'pending', handled_by VARCHAR(32), handled_at BIGINT, handle_comment TEXT, applied_content TEXT, applied_checksum VARCHAR(128), created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS knowledge_reference_log (id VARCHAR(32) PRIMARY KEY, agent_definition_id VARCHAR(32), conversation_id VARCHAR(32), message_id VARCHAR(32), knowledge_base_id VARCHAR(32) NOT NULL, document_id VARCHAR(32) NOT NULL, document_version_id VARCHAR(32), chunk_id VARCHAR(32) NOT NULL, similarity DOUBLE PRECISION, citation_no INTEGER, referenced_at BIGINT NOT NULL, created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0);
CREATE INDEX IF NOT EXISTS knowledge_document_version_idx_document ON knowledge_document_version (knowledge_document_id, version_no, deleted);
CREATE INDEX IF NOT EXISTS knowledge_index_job_idx_status ON knowledge_index_job (status, created_at, deleted);
CREATE INDEX IF NOT EXISTS knowledge_review_task_idx_assignee ON knowledge_review_task (reviewer_id, status, deleted);
CREATE INDEX IF NOT EXISTS knowledge_review_task_idx_document ON knowledge_review_task (document_id, submitted_at, deleted);
CREATE INDEX IF NOT EXISTS knowledge_ai_review_idx_version ON knowledge_ai_review (document_version_id, created_at, deleted);
UPDATE knowledge_document_version SET review_status = 'APPROVED'
WHERE index_status = 2 AND review_status = 'DRAFT';
UPDATE knowledge_document SET review_status = 'APPROVED'
WHERE current_version_no > 0 AND review_status = 'DRAFT';
CREATE INDEX IF NOT EXISTS knowledge_reference_log_idx_document ON knowledge_reference_log (document_id, referenced_at, deleted);

-- 兼容旧版本的 (document_id, chunk_index, deleted) 唯一索引。
-- 旧索引会导致第二次逻辑删除同一分块时与历史记录冲突。
DROP INDEX IF EXISTS uk_knowledge_document_chunk;
DROP INDEX IF EXISTS uk_knowledge_document_chunk_active;
CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_document_chunk_version_active
    ON knowledge_document_chunk (document_version_id, chunk_index) WHERE deleted = FALSE AND document_version_id IS NOT NULL;

-- 为迁移前的活动分块回填其当前版本；无法匹配的旧分块保留但不参与新检索。
UPDATE knowledge_document_chunk chunk
SET document_version_id = version.id
FROM knowledge_document document
JOIN knowledge_document_version version
  ON version.knowledge_document_id = document.id
 AND version.version_no = document.current_version_no
 AND version.deleted = FALSE
WHERE chunk.document_id = document.id
  AND chunk.deleted = FALSE
  AND chunk.document_version_id IS NULL;

-- =====================================================
-- sys_admin_preference (redesigned)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_admin_preference (
    id              VARCHAR(32)  NOT NULL PRIMARY KEY,
    admin_id        VARCHAR(32)  NOT NULL,
    category        VARCHAR(32)  NOT NULL,
    key_name        VARCHAR(128) NOT NULL,
    value           VARCHAR(512) NOT NULL,
    description     VARCHAR(256),
    priority        INT          NOT NULL DEFAULT 50,
    scope           VARCHAR(32)  NOT NULL DEFAULT 'global',
    scope_detail    VARCHAR(64)  NOT NULL DEFAULT '',
    source          VARCHAR(16)  NOT NULL DEFAULT 'explicit',
    confidence      DECIMAL(4,2) NOT NULL DEFAULT 1.00,
    usage_count     INT          NOT NULL DEFAULT 0,
    last_used_at    BIGINT,
    expires_at      BIGINT,
    decay_rate      DECIMAL(4,2) NOT NULL DEFAULT 0.00,
    effective_score DECIMAL(6,2) NOT NULL DEFAULT 100.00,
    status          SMALLINT     NOT NULL DEFAULT 1,
    created_at      BIGINT       NOT NULL,
    updated_at      BIGINT       NOT NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_num        INT          NOT NULL DEFAULT 0,
    state           INT          NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS sys_admin_preference_idx_admin_id ON sys_admin_preference(admin_id);
CREATE INDEX IF NOT EXISTS sys_admin_preference_idx_admin_category ON sys_admin_preference(admin_id, category);
CREATE INDEX IF NOT EXISTS sys_admin_preference_idx_admin_key ON sys_admin_preference(admin_id, key_name);
CREATE UNIQUE INDEX IF NOT EXISTS sys_admin_preference_uk_identity
    ON sys_admin_preference(admin_id, category, key_name, scope, scope_detail)
    WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS sys_admin_preference_idx_expires ON sys_admin_preference(expires_at);
CREATE INDEX IF NOT EXISTS sys_admin_preference_idx_effective ON sys_admin_preference(admin_id, effective_score);

-- =====================================================
-- sys_admin_preference_event
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_admin_preference_event (
    id               VARCHAR(32)  NOT NULL PRIMARY KEY,
    admin_id         VARCHAR(32)  NOT NULL,
    preference_id    VARCHAR(32),
    event_type       VARCHAR(16)  NOT NULL,
    category         VARCHAR(32),
    key_name         VARCHAR(128),
    value            VARCHAR(512),
    confidence       DECIMAL(4,2),
    conversation_id  VARCHAR(32),
    message_id       VARCHAR(32),
    context_snapshot TEXT,
    created_at       BIGINT       NOT NULL,
    updated_at       BIGINT       NOT NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_num        INT          NOT NULL DEFAULT 0,
    state           INT          NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS sys_admin_preference_event_idx_admin_id ON sys_admin_preference_event(admin_id);
CREATE INDEX IF NOT EXISTS sys_admin_preference_event_idx_admin_event ON sys_admin_preference_event(admin_id, event_type);
CREATE INDEX IF NOT EXISTS sys_admin_preference_event_idx_created ON sys_admin_preference_event(created_at);

-- ============================================================================
-- Consolidated source: V2__data.sql
-- ============================================================================
-- Aether PostgreSQL seed/data.
-- Flyway V2 seed data for development and test environments only.
-- Do not run this file before importing production data from MySQL.


-- Source: api/src/main/resources/sql/postgresql/parts/003-system-seed.sql

-- System reference data. Run only for an empty PostgreSQL database.

INSERT INTO msg_email VALUES (1947577954439671809, 1947195431817801729, '2367283463@qq.com', 'Message_Type_Verification', '637566', '登录验证码', '您的验证码是：637566', 0, 1753173743247, 1753174038972, FALSE, 1);
INSERT INTO msg_email VALUES (1947581264542453762, 1947195431817801729, '2367283463@qq.com', 'Message_Type_Verification', '689726', '登录验证码', '您的验证码是：689726', 2, 1753174532436, 1753174532436, FALSE, 1);
INSERT INTO sys_dict VALUES (3, 'Message_Constant', 'Message', 'Message Constant', '消息常量', NULL, NULL, 1, FALSE, 1726196324, 1726196324, 1);
INSERT INTO sys_dict VALUES (4, 'Message_Email_From', 'Message_Constant', 'System Mailbox', '系统邮箱', 'sun.summer.day@qq.com', NULL, 1, FALSE, 1726196324, 1731994580433, 1);
INSERT INTO sys_dict VALUES (5, 'Captcha', NULL, 'Whether the email verification code is enabled', '邮箱验证码是否开启', 'true', 'true，不发送验证码，也不校验', 1, FALSE, 1726196324, 1734922204343, 99);
INSERT INTO sys_dict VALUES (6, 'Resource_Type', NULL, 'Resource Type', '资源类型', NULL, NULL, 1, FALSE, 1726196324, 1726196324, 1);
INSERT INTO sys_dict VALUES (7, 'Resource_Type_Route', 'Resource_Type', 'Route', '路由', NULL, NULL, 1, FALSE, 1726196324, 1726196324, 1);
INSERT INTO sys_dict VALUES (8, 'Resource_Type_Permission', 'Resource_Type', 'Permission', '权限', NULL, NULL, 1, FALSE, 1726196324, 1726196324, 1);
INSERT INTO sys_dict VALUES (10, 'Gender_Type', NULL, 'Gender Type', '性别类型', NULL, NULL, 1, FALSE, 1732157196914, 1732157196914, 1);
INSERT INTO sys_dict VALUES (11, 'Gender_Type_Man', 'Gender_Type', 'Man', '男', NULL, NULL, 1, FALSE, 1732157246505, 1732157246505, 1);
INSERT INTO sys_dict VALUES (12, 'Gender_Type_Woman', 'Gender_Type', 'Woman', '女', '', NULL, 1, FALSE, 1732157288146, 1732506856975, 1);
INSERT INTO sys_dict VALUES (13, 'Gender_Type_Other', 'Gender_Type', 'Other', '其他', '', NULL, 1, FALSE, 1732157317983, 1732157883949, 1);
INSERT INTO sys_dict VALUES (14, 'System_Role', NULL, 'System Role', '系统角色', NULL, NULL, 1, FALSE, 1732157975145, 1732157975145, 1);
INSERT INTO sys_dict VALUES (15, 'System_Role_User', 'System_Role', 'System', '系统', '', NULL, 1, FALSE, 1732158021058, 1732162929093, 1);
INSERT INTO sys_dict VALUES (16, 'System_Role_Tenant', 'System_Role', 'Tenant', '租户', NULL, NULL, 1, FALSE, 1732158093374, 1732158093374, 2);
INSERT INTO sys_dict VALUES (22, 'email_template_login_subject', 'email_template', 'Login Verification Code', '登录验证码', '', '登录邮件标题', 1, FALSE, 1734682294, 1734682294, 1);
INSERT INTO sys_dict VALUES (
    23,
    'email_template_login_content',
    'email_template',
    'Login Verification Code: $' || '{code}',
    '您的验证码是：$' || '{code}',
    '',
    '登录邮件内容',
    1,
    FALSE,
    1734682294,
    1734682294,
    1
);
INSERT INTO sys_dict VALUES (24, 'email_template', NULL, 'Email Template', '邮件模板', '', '邮件模板', 1, FALSE, 1734686432, 1734686432, 1);
INSERT INTO sys_dict VALUES (28, 'Message', NULL, 'Message Dict', '消息字典', NULL, NULL, 1, FALSE, 1734921807446, 1734921807446, 1);
INSERT INTO sys_dict VALUES (29, 'Message_Type', 'Message', 'Message Type', '消息类型', NULL, NULL, 1, FALSE, 1734922178599, 1734922178599, 0);
INSERT INTO sys_dict VALUES (30, 'Message_Type_Notification', 'Message_Type', 'Notification', '通知', NULL, NULL, 1, FALSE, 1734922283133, 1734922939573, 1);
INSERT INTO sys_dict VALUES (31, 'Message_Type_Verification', 'Message_Type', 'Verification Code', '验证码', NULL, NULL, 1, FALSE, 1734922363145, 1734922950949, 1);
INSERT INTO sys_resource VALUES (1, 'System Administration', '系统管理', '/sys', 'Resource_Type_Route', 'SettingOutlined', 0, FALSE, 'Configure administrators, roles, resources, dictionaries, and preferences / 配置管理员、角色、资源、字典与偏好', 0, FALSE, 1, 1753080218236, 3);
INSERT INTO sys_resource VALUES (2, 'Resource & Permission Management', '资源与权限管理', '/sys/resource', 'Resource_Type_Route', NULL, 1, TRUE, 'Manage menus, routes, and permission resources / 管理菜单、路由与权限资源', 0, FALSE, 1, 3, 1);
INSERT INTO sys_resource VALUES (3, 'Role Management', '角色管理', '/sys/role', 'Resource_Type_Route', NULL, 1, TRUE, 'Manage roles and their resource permissions / 管理角色及其资源权限', 0, FALSE, 1, 2, 1);
INSERT INTO sys_resource VALUES (4, 'Dictionary Management', '字典管理', '/sys/dict', 'Resource_Type_Route', NULL, 1, TRUE, 'Maintain system dictionaries and localized labels / 维护系统字典与多语言标签', 0, FALSE, 1, 4, 1);
INSERT INTO sys_resource VALUES (5, 'Administrator Management', '管理员管理', '/sys/admin', 'Resource_Type_Route', NULL, 1, TRUE, 'Manage administrator accounts and role assignments / 管理管理员账号与角色分配', 0, FALSE, 1, 1, 1);
INSERT INTO sys_resource VALUES (10, 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 5, TRUE, 'View administrator accounts and details / 查看管理员账号与详情', 0, FALSE, 1, 1, 1);
INSERT INTO sys_resource VALUES (11, 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 5, TRUE, 'Create, update, disable, and assign administrator roles / 新增、修改、停用管理员并分配角色', 0, FALSE, 1, 2, 1);
INSERT INTO sys_resource VALUES (12, 'Dashboard', '工作台', '/dashboard', 'Resource_Type_Route', 'DashboardOutlined', 0, FALSE, 'View operational summaries and key metrics / 查看运行概览与关键指标', 0, FALSE, 1, 0, 1);
INSERT INTO sys_resource VALUES (13, 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 2, TRUE, 'View menu, route, and permission resources / 查看菜单、路由与权限资源', 0, FALSE, 1, 1, 1);
INSERT INTO sys_resource VALUES (14, 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 2, TRUE, 'Create, update, sort, and remove resources / 新增、修改、排序与删除资源', 0, FALSE, 1, 2, 1);
INSERT INTO sys_resource VALUES (15, 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 3, TRUE, 'Create, update, remove, and authorize roles / 新增、修改、删除角色并配置权限', 0, FALSE, 1, 2, 1);
INSERT INTO sys_resource VALUES (16, 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 3, TRUE, 'View roles and assigned permissions / 查看角色及已分配权限', 0, FALSE, 1, 1, 1);
INSERT INTO sys_resource VALUES (17, 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 4, TRUE, 'View dictionary groups and entries / 查看字典分组与条目', 0, FALSE, 1, 1, 1);
INSERT INTO sys_resource VALUES (18, 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 4, TRUE, 'Create, update, sort, and remove dictionary entries / 新增、修改、排序与删除字典条目', 0, FALSE, 1, 2, 1);
INSERT INTO sys_resource VALUES (24, 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 23, TRUE, 'Manage records retained for legacy compatibility / 管理为兼容旧功能保留的记录', 0, FALSE, 1, 1, 1);
INSERT INTO sys_resource VALUES (25, 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 23, TRUE, 'View records retained for legacy compatibility / 查看为兼容旧功能保留的记录', 0, FALSE, 1, 1, 1);
INSERT INTO sys_resource VALUES (27, 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 21, TRUE, 'Manage configuration retained for legacy compatibility / 管理为兼容旧功能保留的配置', 0, FALSE, 0, 1, 1);
INSERT INTO sys_resource VALUES (28, 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 21, TRUE, 'View configuration retained for legacy compatibility / 查看为兼容旧功能保留的配置', 0, FALSE, 0, 1, 1);
INSERT INTO sys_resource VALUES (29, 'Message Center', '消息中心', '/msg', 'Resource_Type_Route', 'MessageOutlined', 0, FALSE, 'Manage email and SMS delivery records / 管理邮件与短信发送记录', 0, FALSE, 1, 8, 1);
INSERT INTO sys_resource VALUES (30, 'Email Messages', '邮件消息', '/msg/email', 'Resource_Type_Route', NULL, 29, TRUE, 'View and manage email delivery records / 查看与管理邮件发送记录', 0, FALSE, 1, 1, 1);
INSERT INTO sys_resource VALUES (31, 'SMS Messages', '短信消息', '/msg/sms', 'Resource_Type_Route', NULL, 29, TRUE, 'View and manage SMS delivery records / 查看与管理短信发送记录', 0, FALSE, 1, 2, 1);
INSERT INTO sys_resource VALUES (32, 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 30, TRUE, 'Create, resend, or remove email delivery records / 创建、重发或删除邮件发送记录', 0, FALSE, 1, 1, 1);
INSERT INTO sys_resource VALUES (33, 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 30, TRUE, 'View email content and delivery status / 查看邮件内容与发送状态', 0, FALSE, 1, 1, 1);
INSERT INTO sys_resource VALUES (34, 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 31, TRUE, 'Create, resend, or remove SMS delivery records / 创建、重发或删除短信发送记录', 0, FALSE, 1, 1, 1);
INSERT INTO sys_resource VALUES (35, 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 31, TRUE, 'View SMS content and delivery status / 查看短信内容与发送状态', 0, FALSE, 1, 1, 1);
INSERT INTO sys_role VALUES (1, 'root', '超级管理员', 0, FALSE, 1, 1753169908348, 1);
INSERT INTO sys_role VALUES (1947113915888664578, 's s', NULL, 0, TRUE, 1753063107845, 1753063566545, 1);
INSERT INTO sys_role VALUES (1947192336782159874, '1', NULL, 0, TRUE, 1753081804833, 1753081804833, 1);
INSERT INTO sys_role_resource VALUES (1187, 1, 10, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1188, 1, 11, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1189, 1, 12, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1190, 1, 13, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1191, 1, 14, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1192, 1, 15, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1193, 1, 16, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1194, 1, 17, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1195, 1, 18, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1196, 1, 32, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1197, 1, 33, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1198, 1, 5, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1199, 1, 2, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1200, 1, 3, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1201, 1, 4, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1202, 1, 30, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1203, 1, 1, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1204, 1, 31, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1205, 1, 34, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1206, 1, 35, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1207, 1, 29, 0, TRUE, 1, 1, 1);
INSERT INTO sys_role_resource VALUES (1947128411755610113, 1, 10, 0, TRUE, 1753066563930, 1753066563930, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718977, 1, 11, 0, TRUE, 1753066563933, 1753066563933, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718978, 1, 12, 0, TRUE, 1753066563933, 1753066563933, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718979, 1, 13, 0, TRUE, 1753066563934, 1753066563934, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718980, 1, 14, 0, TRUE, 1753066563934, 1753066563934, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718981, 1, 15, 0, TRUE, 1753066563934, 1753066563934, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718982, 1, 16, 0, TRUE, 1753066563934, 1753066563934, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718983, 1, 17, 0, TRUE, 1753066563934, 1753066563934, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718984, 1, 18, 0, TRUE, 1753066563934, 1753066563934, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718985, 1, 32, 0, TRUE, 1753066563935, 1753066563935, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718986, 1, 33, 0, TRUE, 1753066563935, 1753066563935, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718987, 1, 34, 0, TRUE, 1753066563935, 1753066563935, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718988, 1, 35, 0, TRUE, 1753066563935, 1753066563935, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718989, 1, 5, 0, TRUE, 1753066563935, 1753066563935, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718990, 1, 2, 0, TRUE, 1753066563935, 1753066563935, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718991, 1, 3, 0, TRUE, 1753066563935, 1753066563935, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718992, 1, 4, 0, TRUE, 1753066563936, 1753066563936, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718993, 1, 30, 0, TRUE, 1753066563936, 1753066563936, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718994, 1, 31, 0, TRUE, 1753066563936, 1753066563936, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718995, 1, 1, 0, TRUE, 1753066563936, 1753066563936, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718996, 1, 29, 0, TRUE, 1753066563936, 1753066563936, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718997, 1, 6, 0, TRUE, 1753066563936, 1753066563936, 1);
INSERT INTO sys_role_resource VALUES (1947128411822718998, 1, 9, 0, TRUE, 1753066563937, 1753066563937, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292034, 1, 10, 0, TRUE, 1753066571527, 1753066571527, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292035, 1, 11, 0, TRUE, 1753066571528, 1753066571528, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292036, 1, 13, 0, TRUE, 1753066571528, 1753066571528, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292037, 1, 14, 0, TRUE, 1753066571528, 1753066571528, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292038, 1, 15, 0, TRUE, 1753066571529, 1753066571529, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292039, 1, 16, 0, TRUE, 1753066571529, 1753066571529, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292040, 1, 17, 0, TRUE, 1753066571529, 1753066571529, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292041, 1, 18, 0, TRUE, 1753066571529, 1753066571529, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292042, 1, 32, 0, TRUE, 1753066571529, 1753066571529, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292043, 1, 33, 0, TRUE, 1753066571530, 1753066571530, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292044, 1, 34, 0, TRUE, 1753066571530, 1753066571530, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292045, 1, 35, 0, TRUE, 1753066571530, 1753066571530, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292046, 1, 9, 0, TRUE, 1753066571530, 1753066571530, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292047, 1, 5, 0, TRUE, 1753066571530, 1753066571530, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292048, 1, 2, 0, TRUE, 1753066571530, 1753066571530, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292049, 1, 3, 0, TRUE, 1753066571531, 1753066571531, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292050, 1, 4, 0, TRUE, 1753066571531, 1753066571531, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292051, 1, 30, 0, TRUE, 1753066571531, 1753066571531, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292052, 1, 31, 0, TRUE, 1753066571531, 1753066571531, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292053, 1, 1, 0, TRUE, 1753066571531, 1753066571531, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292054, 1, 6, 0, TRUE, 1753066571531, 1753066571531, 1);
INSERT INTO sys_role_resource VALUES (1947128443653292055, 1, 29, 0, TRUE, 1753066571532, 1753066571532, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152002, 1, 10, 0, TRUE, 1753066695740, 1753066695740, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152003, 1, 11, 0, TRUE, 1753066695741, 1753066695741, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152004, 1, 13, 0, TRUE, 1753066695742, 1753066695742, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152005, 1, 14, 0, TRUE, 1753066695742, 1753066695742, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152006, 1, 15, 0, TRUE, 1753066695742, 1753066695742, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152007, 1, 16, 0, TRUE, 1753066695742, 1753066695742, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152008, 1, 17, 0, TRUE, 1753066695743, 1753066695743, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152009, 1, 18, 0, TRUE, 1753066695743, 1753066695743, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152010, 1, 32, 0, TRUE, 1753066695743, 1753066695743, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152011, 1, 33, 0, TRUE, 1753066695743, 1753066695743, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152012, 1, 34, 0, TRUE, 1753066695744, 1753066695744, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152013, 1, 35, 0, TRUE, 1753066695744, 1753066695744, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152014, 1, 5, 0, TRUE, 1753066695744, 1753066695744, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152015, 1, 2, 0, TRUE, 1753066695745, 1753066695745, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152016, 1, 3, 0, TRUE, 1753066695745, 1753066695745, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152017, 1, 4, 0, TRUE, 1753066695745, 1753066695745, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152018, 1, 30, 0, TRUE, 1753066695746, 1753066695746, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152019, 1, 31, 0, TRUE, 1753066695746, 1753066695746, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152020, 1, 1, 0, TRUE, 1753066695746, 1753066695746, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152021, 1, 29, 0, TRUE, 1753066695746, 1753066695746, 1);
INSERT INTO sys_role_resource VALUES (1947128964657152022, 1, 12, 0, TRUE, 1753066695746, 1753066695746, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775361, 1, 10, 0, TRUE, 1753066726710, 1753066726710, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775362, 1, 11, 0, TRUE, 1753066726710, 1753066726710, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775363, 1, 13, 0, TRUE, 1753066726711, 1753066726711, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775364, 1, 14, 0, TRUE, 1753066726711, 1753066726711, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775365, 1, 15, 0, TRUE, 1753066726711, 1753066726711, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775366, 1, 16, 0, TRUE, 1753066726711, 1753066726711, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775367, 1, 17, 0, TRUE, 1753066726711, 1753066726711, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775368, 1, 18, 0, TRUE, 1753066726712, 1753066726712, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775369, 1, 32, 0, TRUE, 1753066726712, 1753066726712, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775370, 1, 33, 0, TRUE, 1753066726712, 1753066726712, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775371, 1, 34, 0, TRUE, 1753066726712, 1753066726712, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775372, 1, 35, 0, TRUE, 1753066726712, 1753066726712, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775373, 1, 5, 0, TRUE, 1753066726713, 1753066726713, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775374, 1, 2, 0, TRUE, 1753066726713, 1753066726713, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775375, 1, 3, 0, TRUE, 1753066726713, 1753066726713, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775376, 1, 4, 0, TRUE, 1753066726714, 1753066726714, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775377, 1, 30, 0, TRUE, 1753066726714, 1753066726714, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775378, 1, 31, 0, TRUE, 1753066726714, 1753066726714, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775379, 1, 1, 0, TRUE, 1753066726715, 1753066726715, 1);
INSERT INTO sys_role_resource VALUES (1947129094533775380, 1, 29, 0, TRUE, 1753066726715, 1753066726715, 1);
INSERT INTO sys_role_resource VALUES (1947190659102502914, 1, 10, 0, TRUE, 1753081404842, 1753081404842, 1);
INSERT INTO sys_role_resource VALUES (1947190659102502915, 1, 11, 0, TRUE, 1753081404849, 1753081404849, 1);
INSERT INTO sys_role_resource VALUES (1947190659102502916, 1, 13, 0, TRUE, 1753081404850, 1753081404850, 1);
INSERT INTO sys_role_resource VALUES (1947190659102502917, 1, 14, 0, TRUE, 1753081404850, 1753081404850, 1);
INSERT INTO sys_role_resource VALUES (1947190659102502918, 1, 15, 0, TRUE, 1753081404851, 1753081404851, 1);
INSERT INTO sys_role_resource VALUES (1947190659102502919, 1, 16, 0, TRUE, 1753081404851, 1753081404851, 1);
INSERT INTO sys_role_resource VALUES (1947190659102502920, 1, 32, 0, TRUE, 1753081404852, 1753081404852, 1);
INSERT INTO sys_role_resource VALUES (1947190659152834561, 1, 33, 0, TRUE, 1753081404852, 1753081404852, 1);
INSERT INTO sys_role_resource VALUES (1947190659152834562, 1, 34, 0, TRUE, 1753081404852, 1753081404852, 1);
INSERT INTO sys_role_resource VALUES (1947190659152834563, 1, 35, 0, TRUE, 1753081404852, 1753081404852, 1);
INSERT INTO sys_role_resource VALUES (1947190659152834564, 1, 5, 0, TRUE, 1753081404853, 1753081404853, 1);
INSERT INTO sys_role_resource VALUES (1947190659152834565, 1, 2, 0, TRUE, 1753081404853, 1753081404853, 1);
INSERT INTO sys_role_resource VALUES (1947190659152834566, 1, 3, 0, TRUE, 1753081404853, 1753081404853, 1);
INSERT INTO sys_role_resource VALUES (1947190659152834567, 1, 30, 0, TRUE, 1753081404854, 1753081404854, 1);
INSERT INTO sys_role_resource VALUES (1947190659152834568, 1, 31, 0, TRUE, 1753081404854, 1753081404854, 1);
INSERT INTO sys_role_resource VALUES (1947190659152834569, 1, 29, 0, TRUE, 1753081404854, 1753081404854, 1);
INSERT INTO sys_role_resource VALUES (1947190659152834570, 1, 17, 0, TRUE, 1753081404855, 1753081404855, 1);
INSERT INTO sys_role_resource VALUES (1947190659152834571, 1, 4, 0, TRUE, 1753081404855, 1753081404855, 1);
INSERT INTO sys_role_resource VALUES (1947190659152834572, 1, 1, 0, TRUE, 1753081404856, 1753081404856, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038465, 1, 10, 0, FALSE, 1753081608512, 1753081608512, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038466, 1, 11, 0, FALSE, 1753081608513, 1753081608513, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038467, 1, 13, 0, FALSE, 1753081608513, 1753081608513, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038468, 1, 14, 0, FALSE, 1753081608513, 1753081608513, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038469, 1, 15, 0, FALSE, 1753081608514, 1753081608514, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038470, 1, 16, 0, FALSE, 1753081608514, 1753081608514, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038471, 1, 32, 0, FALSE, 1753081608514, 1753081608514, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038472, 1, 33, 0, FALSE, 1753081608514, 1753081608514, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038473, 1, 34, 0, FALSE, 1753081608514, 1753081608514, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038474, 1, 35, 0, FALSE, 1753081608515, 1753081608515, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038475, 1, 17, 0, FALSE, 1753081608515, 1753081608515, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038476, 1, 5, 0, FALSE, 1753081608515, 1753081608515, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038477, 1, 2, 0, FALSE, 1753081608515, 1753081608515, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038478, 1, 3, 0, FALSE, 1753081608515, 1753081608515, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038479, 1, 30, 0, FALSE, 1753081608516, 1753081608516, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038480, 1, 31, 0, FALSE, 1753081608516, 1753081608516, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038481, 1, 29, 0, FALSE, 1753081608516, 1753081608516, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038482, 1, 1, 0, FALSE, 1753081608516, 1753081608516, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038483, 1, 4, 0, FALSE, 1753081608516, 1753081608516, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038484, 1, 18, 0, FALSE, 1753081608517, 1753081608517, 1);
INSERT INTO sys_role_resource VALUES (1947191513327038485, 1, 12, 0, FALSE, 1753081608517, 1753081608517, 1);
INSERT INTO sys_role_resource VALUES (1947192354003972097, 1947192336782159874, 29, 0, FALSE, 1753081808945, 1753081808945, 1);
INSERT INTO sys_role_resource VALUES (1947192354003972098, 1947192336782159874, 30, 0, FALSE, 1753081808946, 1753081808946, 1);
INSERT INTO sys_role_resource VALUES (1947192354003972099, 1947192336782159874, 31, 0, FALSE, 1753081808946, 1753081808946, 1);
INSERT INTO sys_role_resource VALUES (1947192354003972100, 1947192336782159874, 32, 0, FALSE, 1753081808946, 1753081808946, 1);
INSERT INTO sys_role_resource VALUES (1947192354003972101, 1947192336782159874, 33, 0, FALSE, 1753081808946, 1753081808946, 1);
INSERT INTO sys_role_resource VALUES (1947192354003972102, 1947192336782159874, 34, 0, FALSE, 1753081808946, 1753081808946, 1);
INSERT INTO sys_role_resource VALUES (1947192354003972103, 1947192336782159874, 35, 0, FALSE, 1753081808946, 1753081808946, 1);
INSERT INTO sys_token VALUES (1945313010319089666, 1945059543981625345, 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeAtbkhM6/1qAf1oGpyu9lku9L7ujLLQ5Ky1M3k7o5pYWlXJDWFZ2FSoBOA1aociTmCh9k2yd/I2dUG6Xe4IJ0oBvTS/JP+1j+gSTI6At6jbgRzL6F0xasdnEJJ5j4fkFwCu6yf/nyLzIjhEiIlIpMF8', 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeADUQC4uViUNROj+q6SPzvg9QE5mFhSsxhwYHN0m565ovKQfvrLlePSMK4TXx2THDrYnlc0BZNkwgkfsrw8fxrZa/1ctC7dGl3cwxNDcxAOunKhy0vRsp1CMqPQzcS1h6DG5PAcZndGAW39FcGuw57M', 1, TRUE, 1752633738486, 1753088815462, 1);
INSERT INTO sys_token VALUES (1947222178596630529, 1945059543981625345, 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeCeNHqZzVbPCSnSRKlGZGOeyGmXUJd2WsDrxv2Ao5QvQfDWptPo2+k4KUyD7pB9UdUUU8aL46YFHndleRzDiv6fsXjr0qCP0EGrp0QOxf5kD3A8QdTsvSgrGFLCF7IMC1d/KtBFyJUVDpIuQuPGnt6A', 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeBKoszhuGCME1OKONwk8dMBMRGY5zK7AGSwVCfr0i8xC3ghRDvO7wPORk7TgsAqEuK4Z65TrM+jWIE0k0xb3lDmaI6gUs5Z1Gsy1QiK15l3ZviLjTJJviCT8x7Vb+q+EtPSi/yEPk3Nw/6Ql6eepW/3', 1, TRUE, 1753088919676, 1753089640109, 1);
INSERT INTO sys_token VALUES (1947229725294473217, 1945059543981625345, 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeDmEusjmEfqqvOiJP4zJzfdpdNLLJhYSOW8bpcmUbouBOCTXuhGswBUYdq55OAycC9nJkOGcCaTdxmBNIB2X1K2f+GvXwPdRMrR/sd18R4JhR9atQVIJyi/WUJgCcL+Pw4Xt5KylVPghKdyDXBQSJ5E', 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeAsFK2/3RopEMxLaebU5NnwkKkKaXH/tdsmXZZLdaUblHL++EpNCRUNWyKNK2zguCgdqk1VSADh8cTVRFxbXEwjfqDigQGl7eOKS5bI62MZX7he6iV+FnUtbpKLu3tF7aRWsKCrBOM6SdpA3Ymz0cio', 1, TRUE, 1753090718947, 1753090765014, 1);
INSERT INTO sys_token VALUES (1947230017838788610, 1945059543981625345, 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeBNctHJsxw2J32sZcgJNEYkK7npaC5JGLtoOTYrXRgLLxPLyTgfJ+a0qSRRz4eLqyvswxfuDD2O01GyDz/9iRkAt+BL+kVIjtSK+l7lB2wThpsIIQ2Nar4zd8MiIjHrdpDKP6IZ/CgBrLMJU+Gvy5QM', 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeCRtSVX77Vl8cWV/KyHY6UvDapLMz5XoIcLhxqZq8AyRcjnPDDFVRiRz5Si6ByLiGdGAuyb2HVstxNENENeu1d1w18Ow1GbTlyXCgd5Z5uO9O8LJcTYbsRFCV1cV6epBfsT/iIcG0tSKF3onVBzRgto', 1, FALSE, 1753090788700, 1753150333164, 1);
INSERT INTO sys_token VALUES (1947501622221586433, 1947195431817801729, 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeB+RnA/QoqZFBYY/IR8aRzaM0fXe8uX+3EGbvHfC61cYy01XnjklHghCYUcdWbKL6GwcE4eeqVExiIfoLY6AxK//2hjz8MHOVOnglO8NhNkjXaladn9rcrOXAqUeFJODQ20sHT6O7pUiEQWi0iz91j2', 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeDxWyCTzMmsrpJFS1ebtvROwf6cfmW2v4VEM7k7y5SYTcA4iQHtEemsKmlZCIL1LcBbhdYpH9tnJMRof+z7BORn/3M9yoRG6y4AXQA6KspFqvY/XwmG0jXzoCYv+o/V3plqOolSktL2OZt7LNACW/BN', 1, TRUE, 1753155544226, 1753156181682, 1);
INSERT INTO sys_token VALUES (1947578153694277633, 1947195431817801729, 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeCAO4V1WxJKX+4mbY141Ameh2ouUGqSDG2xZkQ5Hjd+vxSu67Ij6ntpOxhb3Ztrj50Ze0y4H0G/lXgdRMPA2y7Rf+F3oC56klEVEJsNpshwodVP/jllVA9xH+S8yA5DPuIpYpr6Oohv2m10ctU1z+pS', 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeBRlAjEA5f/6WKsLcJk3m8NMG0jkjkLEiXaRh/G3eK4e4DBV64+2IxucsFpntC0Xc/69R16Zqus5YGBtsjdiJCsbtOTG1/z50XBK7q+ItiwyHs10WdpEjs52pRNXqh80EoDxXCZL0Xc/Xxu00X7stK5', 1, TRUE, 1753173790750, 1753173790743, 1);
INSERT INTO sys_token VALUES (1947581343307288577, 1947195431817801729, 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeBYTApVtCbSaDHPxmUhUz7e8Lw1/1e7lpkaeg6WbU+g5vyH+9vbvoIxkCVewL/f6x/+k+Qx01u7VAcK0wXQeahO0HXXCthofODYMlPU/hwoDlS+1yB+FrbVfDLkwLPvGubNLJz/JQd4heEEhOgIrjHB', 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeAaVzEgv6A9HKfwvWIIrqev6C/vEq2dMnwKdmRXSKTTGAWi9qaHIWnwG+MnWEkBoM1fo2D5v/Q5Vnir2QlqZZeuHRfX2cqpCaONY+tSIcm3fyNM+bsBkJzdALOueoG8GBgujJKE3CPhx8aRNWoW5qYn', 1, FALSE, 1753174551217, 1753174551212, 1);
INSERT INTO sys_user VALUES (1945059543981625345, 'admin', 'Gender_Type_Man', 'System_Role_User', 'admin@163.com', '1345634565', 'https://gw.alipayobjects.com/zos/antfincdn/XAosXuNZyF/BiazfanxmamNRoxxVxka.png', '$2a$10$sAzNmHYKebbZ9BqHE.2PeeIDLTAMCjgtFot7t9wgNvfu35S9/Ouhu', 0, FALSE, 1752573307404, 1753081835834, 1);
INSERT INTO sys_user VALUES (1947195431817801729, 'manger', 'Gender_Type_Woman', 'System_Role_User', '2367283463@qq.com', '64135511', 'https://gw.alipayobjects.com/zos/antfincdn/XAosXuNZyF/BiazfanxmamNRoxxVxka.png', '$2a$10$uQG.Gd2AAp6J9.vBtctRNuYtg32IrCbxewgKUTcotCJLFpRpdLMdK', 0, FALSE, 1753082542754, 1753085765290, 1);
INSERT INTO sys_user_role VALUES (1, 1945059543981625345, 1, 0, TRUE, NULL, NULL, 1);
INSERT INTO sys_user_role VALUES (1947181834177753090, 1945059543981625345, 1, 0, TRUE, 1753079300817, 1753079300817, 1);
INSERT INTO sys_user_role VALUES (1947192406902534145, 1945059543981625345, 1, 0, TRUE, 1753081821554, 1753081821554, 1);
INSERT INTO sys_user_role VALUES (1947192416658485249, 1945059543981625345, 1, 0, TRUE, 1753081823881, 1753081823881, 1);
INSERT INTO sys_user_role VALUES (1947192427626590209, 1945059543981625345, 1, 0, TRUE, 1753081826496, 1753081826496, 1);
INSERT INTO sys_user_role VALUES (1947192449176924162, 1945059543981625345, 1, 0, TRUE, 1753081831629, 1753081831629, 1);
INSERT INTO sys_user_role VALUES (1947192458173706242, 1945059543981625345, 1, 0, TRUE, 1753081833780, 1753081833780, 1);
INSERT INTO sys_user_role VALUES (1947192466784612354, 1945059543981625345, 1, 0, FALSE, 1753081835832, 1753081835832, 1);
INSERT INTO sys_user_role VALUES (1947195431880716290, 1947195431817801729, 1, 0, TRUE, 1753082542764, 1753082542764, 1);
INSERT INTO sys_user_role VALUES (1947208948109242370, 1947195431817801729, 1, 0, FALSE, 1753085765286, 1753085765286, 1);

-- Source: api/src/main/resources/sql/postgresql/parts/004-agent-dictionaries.sql

-- Agent妯″潡瀛楀吀鏁版嵁
-- 鍩轰簬agent-module-dictionaries.md鏂囨。
-- 瀛楀吀琛細sys_dict
-- ID绛栫暐锛氫粠1000寮€濮嬮€掑
-- 鏃堕棿鎴筹細浣跨敤褰撳墠鏃堕棿鎴筹紙1783769933锛?
-- 鐘舵€侊細鍏ㄩ儴鍚敤锛坰tate=1锛?
-- 鍒犻櫎鐘舵€侊細鍏ㄩ儴鏈垹闄わ紙deleted=0锛?
-- 鎺掑簭鍙凤細鐖跺瓧鍏镐负1锛屽瓙瀛楀吀鎸夐『搴忛€掑

SELECT setval(
    'sys_dict_id_seq',
    (COALESCE((SELECT MAX(id::numeric) FROM sys_dict WHERE id ~ '^[0-9]+$'), 0) + 1)::bigint,
    false
);

-- =====================================================
-- 1. Agent_Status
-- =====================================================
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Status', NULL, 'Agent Status', '工具状态', NULL, NULL, 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Status_Disabled', 'Agent_Status', 'Disabled', '已禁用', '0', '已禁用', 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Status_Enabled', 'Agent_Status', 'Enabled', '已启用', '1', '已启用', 1, FALSE, 1783769933, 1783769933, 2);

-- =====================================================
-- 2. Agent_Tool_Type (工具类型)
-- =====================================================
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Tool_Type', NULL, 'Agent Tool Type', '工具类型', NULL, NULL, 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Tool_Type_MCP', 'Agent_Tool_Type', 'MCP', 'MCP', 'mcp', 'MCP 工具', 1, FALSE, 1783769933, 1783769933, 1);

-- =====================================================
-- 3. Agent_Tool_Business_Type (工具业务分类)
-- =====================================================
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Tool_Business_Type', NULL, 'Agent Tool Business Type', '工具业务分类', NULL, NULL, 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Tool_Business_Type_Knowledge', 'Agent_Tool_Business_Type', 'Knowledge', 'Knowledge', 'knowledge', 'Knowledge tool', 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Tool_Business_Type_Ops', 'Agent_Tool_Business_Type', 'Operations', '运维监控', 'ops', '运维监控工具', 1, FALSE, 1783769933, 1783769933, 2);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Tool_Business_Type_Dev', 'Agent_Tool_Business_Type', 'Development', 'Development', 'dev', 'Development collaboration tool', 1, FALSE, 1783769933, 1783769933, 3);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Tool_Business_Type_General', 'Agent_Tool_Business_Type', 'General', '通用', 'general', '通用工具', 1, FALSE, 1783769933, 1783769933, 4);

-- =====================================================
-- 4. Agent_Mcp_Transport (MCP传输类型)
-- =====================================================
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Mcp_Transport', NULL, 'Agent MCP Transport', 'MCP传输类型', NULL, NULL, 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Mcp_Transport_HTTP', 'Agent_Mcp_Transport', 'HTTP', 'HTTP', 'http', 'HTTP MCP transport', 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Mcp_Transport_SSE', 'Agent_Mcp_Transport', 'SSE', 'SSE', 'sse', 'SSE MCP transport', 1, FALSE, 1783769933, 1783769933, 2);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Mcp_Transport_Streamable_HTTP', 'Agent_Mcp_Transport', 'Streamable HTTP', 'Streamable HTTP', 'streamable_http', 'Streamable HTTP MCP transport', 1, FALSE, 1783769933, 1783769933, 3);

-- =====================================================
-- 5. Agent_Mcp_Auth_Type (MCP认证类型)
-- =====================================================
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Mcp_Auth_Type', NULL, 'Agent MCP Auth Type', 'MCP认证类型', NULL, NULL, 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Mcp_Auth_Type_None', 'Agent_Mcp_Auth_Type', 'None', 'None', 'none', 'No authentication', 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Mcp_Auth_Type_Bearer', 'Agent_Mcp_Auth_Type', 'Bearer', 'Bearer Token', 'bearer', 'Bearer Token认证', 1, FALSE, 1783769933, 1783769933, 2);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Mcp_Auth_Type_Api_Key', 'Agent_Mcp_Auth_Type', 'API Key', 'API Key', 'api_key', 'API Key认证', 1, FALSE, 1783769933, 1783769933, 3);

-- =====================================================
-- 6. Agent_Http_Method (HTTP 请求方法)
-- =====================================================
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Http_Method', NULL, 'Agent HTTP Method', 'HTTP 请求方法', NULL, NULL, 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Http_Method_GET', 'Agent_Http_Method', 'GET', 'GET', 'GET', 'GET 请求', 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Http_Method_POST', 'Agent_Http_Method', 'POST', 'POST', 'POST', 'POST 请求', 1, FALSE, 1783769933, 1783769933, 2);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Http_Method_PUT', 'Agent_Http_Method', 'PUT', 'PUT', 'PUT', 'PUT 请求', 1, FALSE, 1783769933, 1783769933, 3);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Http_Method_DELETE', 'Agent_Http_Method', 'DELETE', 'DELETE', 'DELETE', 'DELETE 请求', 1, FALSE, 1783769933, 1783769933, 4);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Http_Method_PATCH', 'Agent_Http_Method', 'PATCH', 'PATCH', 'PATCH', 'PATCH 请求', 1, FALSE, 1783769933, 1783769933, 5);

-- =====================================================
-- 7. Agent_Content_Type (Content-Type 类型)
-- =====================================================
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Content_Type', NULL, 'Agent Content Type', 'Content-Type 类型', NULL, NULL, 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Content_Type_JSON', 'Agent_Content_Type', 'application/json', 'application/json', 'application/json', 'JSON 格式', 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Content_Type_Form', 'Agent_Content_Type', 'application/x-www-form-urlencoded', 'application/x-www-form-urlencoded', 'application/x-www-form-urlencoded', '表单格式', 1, FALSE, 1783769933, 1783769933, 2);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Content_Type_Multipart', 'Agent_Content_Type', 'multipart/form-data', 'multipart/form-data', 'multipart/form-data', '文件上传格式', 1, FALSE, 1783769933, 1783769933, 3);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Content_Type_Plain', 'Agent_Content_Type', 'text/plain', 'text/plain', 'text/plain', 'Plain text format', 1, FALSE, 1783769933, 1783769933, 4);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Content_Type_XML', 'Agent_Content_Type', 'text/xml', 'text/xml', 'text/xml', 'XML 格式', 1, FALSE, 1783769933, 1783769933, 5);

-- =====================================================
-- 8. Agent_Response_Type (响应提取类型)
-- =====================================================
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Response_Type', NULL, 'Agent Response Type', '响应提取类型', NULL, NULL, 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Response_Type_JSONPath', 'Agent_Response_Type', 'JSONPath', 'JSONPath', 'jsonpath', 'JSONPath expression', 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Response_Type_Regex', 'Agent_Response_Type', 'Regex', 'Regex', 'regex', 'Regex expression', 1, FALSE, 1783769933, 1783769933, 2);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Response_Type_Empty', 'Agent_Response_Type', 'Empty', 'Empty', 'empty', 'Return full response', 1, FALSE, 1783769933, 1783769933, 3);

-- =====================================================
-- 9. Model_Provider_Type (模型供应商类型)
-- =====================================================
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Model_Provider_Type', NULL, 'Model Provider Type', '模型供应商类型', NULL, NULL, 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Model_Provider_Type_OpenAI', 'Model_Provider_Type', 'OpenAI', 'OpenAI', 'openai', 'OpenAI model provider', 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Model_Provider_Type_Azure', 'Model_Provider_Type', 'Azure', 'Azure', 'azure', 'Azure OpenAI model provider', 1, FALSE, 1783769933, 1783769933, 2);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Model_Provider_Type_Anthropic', 'Model_Provider_Type', 'Anthropic', 'Anthropic', 'anthropic', 'Anthropic model provider', 1, FALSE, 1783769933, 1783769933, 3);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Model_Provider_Type_Local', 'Model_Provider_Type', 'Local', '本地', 'local', 'Local model provider', 1, FALSE, 1783769933, 1783769933, 4);

-- =====================================================
-- 10. Agent_Definition_Status (Agent 定义状态) 状态：0-草稿，1-启用，2-禁用
-- =====================================================
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Definition_Status', NULL, 'Agent Definition Status', 'Agent定义状态', NULL, NULL, 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Definition_Status_Draft', 'Agent_Definition_Status', 'Draft', '草稿', '0', '草稿状态', 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Definition_Status_Enabled', 'Agent_Definition_Status', 'Enabled', '启用', '1', '启用状态', 1, FALSE, 1783769933, 1783769933, 2);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Definition_Status_Disabled', 'Agent_Definition_Status', 'Disabled', '禁用', '2', '禁用状态', 1, FALSE, 1783769933, 1783769933, 3);



-- =====================================================
-- 11. Agent_Access_Type (访问类型)
-- =====================================================
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Access_Type', NULL, 'Agent Access Type', '访问类型', NULL, NULL, 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Access_Type_Private', 'Agent_Access_Type', 'Private', '私有', 'private', '仅自己可访问', 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Access_Type_Public', 'Agent_Access_Type', 'Public', '公开', 'public', '所有用户可访问', 1, FALSE, 1783769933, 1783769933, 2);

-- =====================================================
-- 12. Agent_Reasoning_Effort (推理力度)
-- =====================================================
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Reasoning_Effort', NULL, 'Agent Reasoning Effort', '推理力度', NULL, NULL, 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Reasoning_Effort_Low', 'Agent_Reasoning_Effort', 'Low', '轻度', 'low', '轻度推理', 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Reasoning_Effort_Medium', 'Agent_Reasoning_Effort', 'Medium', '中度', 'medium', '中度推理', 1, FALSE, 1783769933, 1783769933, 2);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Reasoning_Effort_High', 'Agent_Reasoning_Effort', 'High', '深度', 'high', '深度推理', 1, FALSE, 1783769933, 1783769933, 3);

-- =====================================================
-- 13. Agent_Run_Status (运行状态)
-- =====================================================
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Run_Status', NULL, 'Agent Run Status', '运行状态', NULL, NULL, 1, FALSE, 1783769933, 1783769933, 1);

-- =====================================================
-- 14. Agent_ToolCall_Status (工具调用状态)
-- =====================================================
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_ToolCall_Status', NULL, 'Agent ToolCall Status', '工具调用状态', NULL, NULL, 1, FALSE, 1783769933, 1783769933, 1);

-- ============================================================================
-- Consolidated source: V3__knowledge_review_workflow.sql
-- ============================================================================
-- Strict knowledge document review workflow migration.
-- Flyway executes this migration transactionally after V1 and V2.
-- 15. Agent_Conversation_Status (会话状态) 状态：0-进行中，1-关闭，2-归档
-- =====================================================
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Conversation_Status', NULL, 'Agent Conversation Status', '会话状态', NULL, NULL, 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Conversation_Status_Ongoing', 'Agent_Conversation_Status', 'Ongoing', '进行中', '0', '进行中状态', 1, FALSE, 1783769933, 1783769933, 1);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Conversation_Status_Closed', 'Agent_Conversation_Status', 'Closed', '关闭', '1', '关闭状态', 1, FALSE, 1783769933, 1783769933, 2);
INSERT INTO sys_dict VALUES (nextval('sys_dict_id_seq')::text, 'Agent_Conversation_Status_Archived', 'Agent_Conversation_Status', 'Archived', '归档', '2', '归档状态', 1, FALSE, 1783769933, 1783769933, 3);


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
    IF EXISTS (
        SELECT 1 FROM knowledge_base
        WHERE owner_admin_id IS NULL OR BTRIM(owner_admin_id) = ''
    ) THEN
        IF default_owner_id IS NULL THEN
            RAISE EXCEPTION 'active admin user is required before migrating knowledge-base ownership';
        END IF;
        UPDATE knowledge_base
        SET owner_admin_id = default_owner_id
        WHERE owner_admin_id IS NULL OR BTRIM(owner_admin_id) = '';
    END IF;

    SELECT id, default_model INTO review_provider_id, review_model
    FROM agent_model_provider
    WHERE status = 1 AND deleted = FALSE
      AND COALESCE(default_model, '') NOT ILIKE '%embedding%'
    ORDER BY sort, created_at
    LIMIT 1;
    IF EXISTS (
        SELECT 1 FROM knowledge_base
        WHERE review_config IS NULL OR BTRIM(review_config) = ''
    ) THEN
        IF review_provider_id IS NULL OR COALESCE(review_model, '') = '' THEN
            RAISE EXCEPTION 'an enabled non-embedding model provider is required for AI review';
        END IF;
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
    END IF;
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

-- ============================================================================
-- Consolidated source: V4__agent_knowledge_menu_resources.sql
-- ============================================================================
-- Agent platform and knowledge-base menu resources.
-- Generated from the frontend route configuration. This script is idempotent.
-- It creates menu/permission resources and grants them only to the existing root role.
-- Hidden detail routes intentionally do not have independent menu resources; they inherit
-- access from /knowledge/document.

WITH resources (id, name, name_cn, path, type, icon, parent_id, leaf, description, sort_num) AS (
    VALUES
        -- System preference route
        ('sys_admin_preference', 'Administrator Preferences', '管理员偏好', '/sys/preference', 'Resource_Type_Route', NULL, '1', TRUE, 'Manage personalized administrator preferences and behavior settings / 管理管理员个性化偏好与行为设置', 5),
        ('perm_sys_admin_preference_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'sys_admin_preference', TRUE, 'View preference profiles and usage information / 查看偏好配置与使用信息', 1),
        ('perm_sys_admin_preference_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'sys_admin_preference', TRUE, 'Create, update, reset, and remove preference settings / 新增、修改、重置与删除偏好设置', 2),

        -- Agent platform root and visible routes
        ('menu_agent', 'AI Agent Platform', '智能体平台', '/agent', 'Resource_Type_Route', 'OpenAIOutlined', '0', FALSE, 'Configure AI agents, models, tools, conversations, and execution records / 配置智能体、模型、工具、会话与执行记录', 20),
        ('agent_model_provider', 'Model Providers', '模型服务商', '/agent/model-provider', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, 'Configure model endpoints, credentials, defaults, and context limits / 配置模型端点、凭证、默认模型与上下文限制', 1),
        ('agent_definition', 'Agent Configurations', '智能体配置', '/agent/definition', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, 'Configure agent prompts, models, tools, and access policies / 配置智能体提示词、模型、工具与访问策略', 2),
        ('agent_mcp_server', 'MCP Servers', 'MCP 服务', '/agent/mcp-server', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, 'Configure MCP connections, transport, authentication, and availability / 配置 MCP 连接、传输、认证与可用状态', 3),
        ('agent_tool', 'Tool Catalog', '工具目录', '/agent/tool', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, 'Manage discoverable tools, schemas, classifications, and status / 管理可用工具、参数结构、业务分类与状态', 4),
        ('agent_conversation', 'Conversations', '会话管理', '/agent/conversation', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, 'Review conversation history, messages, lifecycle, and usage / 查看会话历史、消息、生命周期与使用情况', 5),
        ('agent_chat', 'Chat Playground', '对话调试', '/agent/chat', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, 'Test agents, prompts, models, and tool interactions / 测试智能体、提示词、模型与工具交互', 6),
        ('agent_run', 'Execution History', '执行记录', '/agent/run', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, 'Inspect agent executions, token usage, latency, and errors / 查看智能体执行、Token 用量、耗时与错误', 7),
        ('agent_tool_call_log', 'Tool Call Logs', '工具调用日志', '/agent/tool-call-log', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, 'Audit tool requests, responses, latency, and execution status / 审计工具请求、响应、耗时与执行状态', 8),

        -- Knowledge root and visible routes
        ('menu_knowledge', 'Knowledge Center', '知识中心', '/knowledge', 'Resource_Type_Route', 'DatabaseOutlined', '0', FALSE, 'Manage knowledge bases, documents, review workflows, and indexing / 管理知识库、文档、审核流程与索引任务', 21),
        ('knowledge_base', 'Knowledge Bases', '知识库', '/knowledge/base', 'Resource_Type_Route', NULL, 'menu_knowledge', TRUE, 'Configure knowledge scope, visibility, retrieval, review, and embeddings / 配置知识范围、可见性、检索、审核与向量模型', 1),
        ('knowledge_document', 'Knowledge Documents', '知识文档', '/knowledge/document', 'Resource_Type_Route', NULL, 'menu_knowledge', TRUE, 'Create, upload, version, publish, and retrieve knowledge documents / 新建、上传、版本化、发布与检索知识文档', 2),
        ('knowledge_reviews', 'Content Review Center', '内容审核', '/knowledge/reviews', 'Resource_Type_Route', NULL, 'menu_knowledge', TRUE, 'Process AI-assisted and manual document review tasks / 处理 AI 辅助与人工文档审核任务', 3),
        ('knowledge_index_job', 'Indexing Jobs', '索引任务', '/knowledge/index-job', 'Resource_Type_Route', NULL, 'menu_knowledge', TRUE, 'Monitor indexing progress, retries, statistics, and failures / 监控索引进度、重试、统计与失败信息', 4),

        -- Agent route permissions
        ('perm_agent_model_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_model_provider', TRUE, 'View model provider configuration and availability / 查看模型服务商配置与可用状态', 1),
        ('perm_agent_model_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_model_provider', TRUE, 'Create, update, test, enable, and disable model providers / 新增、修改、测试、启用与停用模型服务商', 2),
        ('perm_agent_definition_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_definition', TRUE, 'View agent definitions, prompts, bindings, and status / 查看智能体定义、提示词、绑定与状态', 1),
        ('perm_agent_definition_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_definition', TRUE, 'Create, update, enable, disable, and remove agent configurations / 新增、修改、启停与删除智能体配置', 2),
        ('perm_agent_mcp_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_mcp_server', TRUE, 'View MCP connection configuration and health / 查看 MCP 连接配置与健康状态', 1),
        ('perm_agent_mcp_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_mcp_server', TRUE, 'Create, update, test, enable, and disable MCP servers / 新增、修改、测试、启用与停用 MCP 服务', 2),
        ('perm_agent_tool_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_tool', TRUE, 'View tools, schemas, classifications, and availability / 查看工具、参数结构、分类与可用状态', 1),
        ('perm_agent_tool_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_tool', TRUE, 'Synchronize, update, enable, disable, and bind tools / 同步、修改、启停与绑定工具', 2),
        ('perm_agent_conversation_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_conversation', TRUE, 'View conversations, messages, summaries, and usage / 查看会话、消息、摘要与使用情况', 1),
        ('perm_agent_conversation_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_conversation', TRUE, 'Update lifecycle state and remove conversation records / 更新会话生命周期并删除会话记录', 2),
        ('perm_agent_chat_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_chat', TRUE, 'Open the playground and view debugging sessions / 进入调试台并查看调试会话', 1),
        ('perm_agent_chat_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_chat', TRUE, 'Send test messages and execute configured tools / 发送测试消息并执行已配置工具', 2),
        ('perm_agent_run_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_run', TRUE, 'View execution details, usage, latency, and errors / 查看执行详情、用量、耗时与错误', 1),
        ('perm_agent_run_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_run', TRUE, 'Retry, terminate, or remove execution records / 重试、终止或删除执行记录', 2),
        ('perm_agent_tool_log_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_tool_call_log', TRUE, 'View tool request, response, status, and latency / 查看工具请求、响应、状态与耗时', 1),
        ('perm_agent_tool_log_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_tool_call_log', TRUE, 'Remove or maintain tool call audit records / 删除或维护工具调用审计记录', 2),

        -- Knowledge route permissions
        ('perm_knowledge_base_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'knowledge_base', TRUE, 'View accessible knowledge bases and retrieval configuration / 查看可访问知识库及检索配置', 1),
        ('perm_knowledge_base_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'knowledge_base', TRUE, 'Create, update, configure, and remove knowledge bases / 新增、修改、配置与删除知识库', 2),
        ('perm_knowledge_document_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'knowledge_document', TRUE, 'View documents, versions, chunks, and source previews / 查看文档、版本、分块与原文件预览', 1),
        ('perm_knowledge_document_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'knowledge_document', TRUE, 'Create, upload, edit, submit, publish, and remove documents / 新建、上传、编辑、提交、发布与删除文档', 2),
        ('perm_knowledge_reviews_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'knowledge_reviews', TRUE, 'View review queues, AI findings, decisions, and audit history / 查看审核队列、AI 问题、审核结论与审计历史', 1),
        ('perm_knowledge_reviews_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'knowledge_reviews', TRUE, 'Claim tasks, handle findings, approve, or reject documents / 认领任务、处理问题并批准或驳回文档', 2),
        ('perm_knowledge_index_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'knowledge_index_job', TRUE, 'View indexing progress, statistics, retries, and errors / 查看索引进度、统计、重试与错误', 1),
        ('perm_knowledge_index_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'knowledge_index_job', TRUE, 'Queue, retry, and maintain document indexing jobs / 创建、重试与维护文档索引任务', 2)
)
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description,
                          state, deleted, created_at, updated_at, sort_num)
SELECT id, name, name_cn, path, type, icon, parent_id, leaf, description,
       0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, sort_num
FROM resources
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    name_cn = EXCLUDED.name_cn,
    path = EXCLUDED.path,
    type = EXCLUDED.type,
    icon = EXCLUDED.icon,
    parent_id = EXCLUDED.parent_id,
    leaf = EXCLUDED.leaf,
    description = EXCLUDED.description,
    sort_num = EXCLUDED.sort_num,
    deleted = FALSE,
    updated_at = EXCLUDED.updated_at;

-- Give the root role all newly registered menus and permissions. Other roles must be
-- assigned deliberately: review/index API access is enforced by /knowledge/document.
WITH root_role AS (
    SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1
), target_resources AS (
    SELECT id FROM sys_resource WHERE id IN (
        'sys_admin_preference', 'perm_sys_admin_preference_read', 'perm_sys_admin_preference_write',
        'menu_agent', 'agent_model_provider', 'agent_definition', 'agent_mcp_server', 'agent_tool',
        'agent_conversation', 'agent_chat', 'agent_run', 'agent_tool_call_log',
        'menu_knowledge', 'knowledge_base', 'knowledge_document', 'knowledge_reviews', 'knowledge_index_job',
        'perm_agent_model_read', 'perm_agent_model_write', 'perm_agent_definition_read', 'perm_agent_definition_write',
        'perm_agent_mcp_read', 'perm_agent_mcp_write', 'perm_agent_tool_read', 'perm_agent_tool_write',
        'perm_agent_conversation_read', 'perm_agent_conversation_write', 'perm_agent_chat_read', 'perm_agent_chat_write',
        'perm_agent_run_read', 'perm_agent_run_write', 'perm_agent_tool_log_read', 'perm_agent_tool_log_write',
        'perm_knowledge_base_read', 'perm_knowledge_base_write', 'perm_knowledge_document_read', 'perm_knowledge_document_write',
        'perm_knowledge_reviews_read', 'perm_knowledge_reviews_write', 'perm_knowledge_index_read', 'perm_knowledge_index_write'
    )
)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('root:' || root_role.id || ':' || target_resources.id), root_role.id, target_resources.id,
       0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM root_role CROSS JOIN target_resources
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_resource existing
    WHERE existing.role_id = root_role.id
      AND existing.resource_id = target_resources.id
      AND existing.deleted = FALSE
);

-- ============================================================================
-- Consolidated source: V6__knowledge_ai_review_diff.sql
-- ============================================================================
ALTER TABLE knowledge_ai_review
    ADD COLUMN IF NOT EXISTS source_content TEXT;

ALTER TABLE knowledge_ai_review_issue
    ADD COLUMN IF NOT EXISTS applied_content TEXT,
    ADD COLUMN IF NOT EXISTS applied_checksum VARCHAR(128);

-- ============================================================================
-- Consolidated source: V7__model_provider_context_window.sql
-- ============================================================================
ALTER TABLE agent_model_provider
    ADD COLUMN IF NOT EXISTS context_window INTEGER NOT NULL DEFAULT 32768;

-- ============================================================================
-- Consolidated source: V8__conversation_summary_persistence.sql
-- ============================================================================
ALTER TABLE agent_conversation
    ADD COLUMN IF NOT EXISTS summary TEXT,
    ADD COLUMN IF NOT EXISTS summary_covered_message_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS summary_covered_created_at BIGINT,
    ADD COLUMN IF NOT EXISTS summary_updated_at BIGINT;

