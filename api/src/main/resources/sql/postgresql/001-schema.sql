-- Aether PostgreSQL schema.
-- Contains table definitions, indexes and pgvector infrastructure only.
-- Run before 002-data.sql for local initialization or before data import for migration.

\set ON_ERROR_STOP on


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
DROP TABLE IF EXISTS msg_email CASCADE;
CREATE TABLE msg_email (
                              id VARCHAR(32) NOT NULL,
                              user_id VARCHAR(32) NOT NULL,
                              email varchar(255) NOT NULL,
                              type varchar(255) NOT NULL,
                              code varchar(255) NULL,
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
DROP TABLE IF EXISTS msg_sms CASCADE;
CREATE TABLE msg_sms (
                            id VARCHAR(32) NOT NULL,
                            user_id VARCHAR(32) NOT NULL,
                            phone varchar(255) NOT NULL,
                            code varchar(255) NOT NULL,
                            type varchar(255) NOT NULL,
                            subject varchar(255) NOT NULL,
                            body text NOT NULL,
                            state INTEGER NOT NULL DEFAULT 0,
                            deleted BOOLEAN NULL DEFAULT FALSE,
                            created_at TIMESTAMPTZ NOT NULL,
                            updated_at TIMESTAMPTZ NOT NULL,
                            sort_num INTEGER NOT NULL DEFAULT 1,
                            PRIMARY KEY (id)

);

-- ----------------------------
-- Records of msg_sms
-- ----------------------------

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS sys_config CASCADE;
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
DROP TABLE IF EXISTS sys_dict CASCADE;
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
DROP TABLE IF EXISTS sys_resource CASCADE;
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
DROP TABLE IF EXISTS sys_role CASCADE;
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
DROP TABLE IF EXISTS sys_role_resource CASCADE;
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
DROP TABLE IF EXISTS sys_token CASCADE;
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
DROP TABLE IF EXISTS sys_user CASCADE;
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
DROP TABLE IF EXISTS sys_user_role CASCADE;
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
DROP TABLE IF EXISTS user_member CASCADE;
CREATE TABLE user_member (
                                id VARCHAR(32) NOT NULL,
                                name varchar(255) NULL,
                                username varchar(255) NULL,
                                password varchar(255) NULL,
                                phone varchar(255) NULL,
                                email varchar(255) NULL,
                                avatar varchar(255) NULL,
                                state BOOLEAN NULL DEFAULT FALSE,
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
-- 11. agent_knowledge_base锛堢煡璇嗗簱 鈥?V0.7棰勭暀锛?-- =====================================================
CREATE TABLE IF NOT EXISTS agent_knowledge_base (
    id VARCHAR(32)       NOT NULL PRIMARY KEY,
    agent_definition_id VARCHAR(32)       NOT NULL,
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
-- 12. agent_document锛堟枃妗?鈥?V0.7棰勭暀锛?-- =====================================================
CREATE TABLE IF NOT EXISTS agent_document (
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
CREATE INDEX IF NOT EXISTS agent_knowledge_base_idx_agent_id ON agent_knowledge_base (agent_definition_id);
CREATE INDEX IF NOT EXISTS agent_knowledge_base_idx_status ON agent_knowledge_base (state);
CREATE INDEX IF NOT EXISTS agent_document_idx_knowledge_base_id ON agent_document (knowledge_base_id);
CREATE INDEX IF NOT EXISTS agent_document_idx_status ON agent_document (state);

-- Source: api/src/main/resources/sql/postgresql/parts/005-pgvector.sql

-- pgvector foundation for the future knowledge-base retrieval capability.
-- The OpenAI-compatible text-embedding-3-small model uses 1536 dimensions.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS agent_document_chunk (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    knowledge_base_id VARCHAR(32) NOT NULL,
    document_id VARCHAR(32) NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER NOT NULL DEFAULT 0,
    embedding vector(1536) NOT NULL,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_agent_document_chunk UNIQUE (document_id, chunk_index, deleted)
);

CREATE INDEX IF NOT EXISTS idx_agent_document_chunk_knowledge_base
    ON agent_document_chunk (knowledge_base_id, deleted);
CREATE INDEX IF NOT EXISTS idx_agent_document_chunk_document
    ON agent_document_chunk (document_id, deleted, chunk_index);
CREATE INDEX IF NOT EXISTS idx_agent_document_chunk_embedding_cosine
    ON agent_document_chunk USING hnsw (embedding vector_cosine_ops);

