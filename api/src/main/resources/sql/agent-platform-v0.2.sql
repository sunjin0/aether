-- agent-platform V0.2 建表脚本
-- 表前缀：agent_
-- 所有表继承 BaseEntity 公共字段：id, created_at, updated_at, sort_num, deleted, state

-- =====================================================
-- 1. agent_model_provider（模型供应商）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_model_provider` (
    `id`              BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `name`            VARCHAR(64)  NOT NULL COMMENT '供应商名称',
    `type`            VARCHAR(32)  NOT NULL COMMENT '供应商类型：openai、azure、anthropic、local',
    `api_base_url`    VARCHAR(256)          COMMENT 'API基础地址',
    `api_key`         VARCHAR(512)          COMMENT 'API Key（AES加密存储）',
    `default_model`   VARCHAR(64)           COMMENT '默认模型名称',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `sort`            INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `remark`          VARCHAR(512)          COMMENT '备注',
    `created_at`      BIGINT                COMMENT '创建时间',
    `updated_at`      BIGINT                COMMENT '更新时间',
    `sort_num`        INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`           INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    UNIQUE KEY `uk_name` (`name`, `deleted`),
    KEY `idx_type` (`type`),
    KEY `idx_status` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型供应商';

-- =====================================================
-- 2. agent_definition（Agent定义）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_definition` (
    `id`                  BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `name`                VARCHAR(64)  NOT NULL COMMENT 'Agent名称',
    `code`                VARCHAR(64)  NOT NULL COMMENT 'Agent编码（唯一）',
    `description`         VARCHAR(512)          COMMENT '描述',
    `system_prompt`       TEXT                  COMMENT '系统提示词',
    `model_provider_id`   BIGINT                COMMENT '关联模型供应商ID',
    `model`               VARCHAR(64)           COMMENT '使用的模型名称',
    `temperature`         DECIMAL(3,2) NOT NULL DEFAULT 0.70 COMMENT '温度参数',
    `max_tokens`          INT          NOT NULL DEFAULT 2048 COMMENT '最大token数',
    `status`              TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-草稿，1-启用，2-禁用',
    `max_tool_rounds`     INT          NOT NULL DEFAULT 1 COMMENT '最大工具调用轮次（V0.6预留）',
    `default_thinking`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '默认是否启用深度思考',
    `default_reasoning_effort` VARCHAR(16)      DEFAULT NULL COMMENT '默认推理力度：low/medium/high',
    `access_type`         VARCHAR(16)  NOT NULL DEFAULT 'private' COMMENT '访问类型：private/public（V1.0预留）',
    `sort`                INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `remark`              VARCHAR(512)          COMMENT '备注',
    `created_at`          BIGINT                COMMENT '创建时间',
    `updated_at`          BIGINT                COMMENT '更新时间',
    `sort_num`            INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`               INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    UNIQUE KEY `uk_code` (`code`, `deleted`),
    KEY `idx_name` (`name`),
    KEY `idx_model_provider_id` (`model_provider_id`),
    KEY `idx_status` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent定义';

-- =====================================================
-- 3. agent_tool（工具）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_tool` (
    `id`                    BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `name`                  VARCHAR(64)  NOT NULL COMMENT '工具名称',
    `code`                  VARCHAR(64)  NOT NULL COMMENT '工具编码（唯一）',
    `description`           VARCHAR(512)          COMMENT '描述',
    `type`                  VARCHAR(32)  NOT NULL DEFAULT 'http' COMMENT '工具类型：http',
    `http_method`           VARCHAR(16)           COMMENT 'HTTP方法：GET、POST',
    `http_url`              VARCHAR(512)          COMMENT 'HTTP请求地址',
    `http_headers`          TEXT                  COMMENT '请求头模板（JSON格式）',
    `http_body_template`    TEXT                  COMMENT '请求体模板（支持占位符）',
    `response_extract_rule` VARCHAR(512)          COMMENT '响应提取规则（JSONPath或正则）',
    `timeout_ms`            INT          NOT NULL DEFAULT 30000 COMMENT '超时时间（毫秒）',
    `cache_ttl_seconds`     INT          NOT NULL DEFAULT 0 COMMENT '缓存TTL（秒），默认0表示不缓存（V0.6预留）',
    `status`                TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `sort`                  INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `remark`                VARCHAR(512)          COMMENT '备注',
    `created_at`            BIGINT                COMMENT '创建时间',
    `updated_at`            BIGINT                COMMENT '更新时间',
    `sort_num`              INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`               TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`                 INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    UNIQUE KEY `uk_code` (`code`, `deleted`),
    KEY `idx_name` (`name`),
    KEY `idx_type` (`type`),
    KEY `idx_status` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具';

-- =====================================================
-- 4. agent_tool_binding（工具绑定）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_tool_binding` (
    `id`                    BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `agent_definition_id`   BIGINT       NOT NULL COMMENT '关联Agent定义ID',
    `tool_id`               BIGINT       NOT NULL COMMENT '关联工具ID',
    `priority`              INT          NOT NULL DEFAULT 0 COMMENT '调用优先级',
    `status`                TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `created_at`            BIGINT                COMMENT '创建时间',
    `updated_at`            BIGINT                COMMENT '更新时间',
    `sort_num`              INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`               TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`                 INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    UNIQUE KEY `uk_agent_tool` (`agent_definition_id`, `tool_id`, `deleted`),
    KEY `idx_agent_id` (`agent_definition_id`),
    KEY `idx_tool_id` (`tool_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具绑定';

-- =====================================================
-- 5. agent_conversation（会话）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_conversation` (
    `id`                  BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `user_id`             BIGINT       NOT NULL COMMENT '用户ID',
    `agent_definition_id` BIGINT       NOT NULL COMMENT '关联Agent定义ID',
    `title`               VARCHAR(256)          COMMENT '会话标题',
    `message_count`       INT          NOT NULL DEFAULT 0 COMMENT '消息数',
    `status`              TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-进行中，1-关闭，2-归档',
    `created_at`          BIGINT                COMMENT '创建时间',
    `updated_at`          BIGINT                COMMENT '更新时间',
    `sort_num`            INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`               INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    KEY `idx_user_agent` (`user_id`, `agent_definition_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_agent_id` (`agent_definition_id`),
    KEY `idx_status` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话';

-- =====================================================
-- 6. agent_message（消息）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_message` (
    `id`                BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `conversation_id`   BIGINT       NOT NULL COMMENT '关联会话ID',
    `role`              VARCHAR(16)  NOT NULL COMMENT '角色：user、assistant、tool',
    `content`           LONGTEXT              COMMENT '消息内容',
    `reasoning_content` LONGTEXT              COMMENT '推理内容（assistant角色时）',
    `tool_calls`        TEXT                  COMMENT '工具调用请求（JSON格式，assistant角色时）',
    `tool_call_id`      VARCHAR(64)           COMMENT '工具调用ID（tool角色时）',
    `tool_result`       TEXT                  COMMENT '工具调用结果（tool角色时）',
    `model`             VARCHAR(64)           COMMENT '使用的模型（assistant角色时）',
    `prompt_tokens`     INT                   COMMENT '输入token数',
    `completion_tokens` INT                   COMMENT '输出token数',
    `total_tokens`      INT                   COMMENT '总token数',
    `reasoning_tokens`  INT                   COMMENT '推理token数',
    `latency_ms`        INT                   COMMENT '响应延迟（毫秒）',
    `edited`            TINYINT      NOT NULL DEFAULT 0 COMMENT '是否编辑：0-未编辑，1-已编辑（V0.6预留）',
    `original_content`  LONGTEXT              COMMENT '编辑前的原始内容（V0.6预留）',
    `edited_at`         BIGINT                COMMENT '编辑时间（V0.6预留）',
    `created_at`        BIGINT                COMMENT '创建时间',
    `updated_at`        BIGINT                COMMENT '更新时间',
    `sort_num`          INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`             INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_role` (`role`),
    KEY `idx_create_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息';

-- =====================================================
-- 7. agent_run（运行记录）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_run` (
    `id`                  BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `agent_definition_id` BIGINT       NOT NULL COMMENT '关联Agent定义ID',
    `user_id`             BIGINT       NOT NULL COMMENT '用户ID',
    `conversation_id`     BIGINT                COMMENT '关联会话ID',
    `message_id`          BIGINT                COMMENT '关联输出消息ID',
    `input_content`       TEXT                  COMMENT '输入内容摘要',
    `output_content`      TEXT                  COMMENT '输出内容摘要',
    `model`               VARCHAR(64)           COMMENT '使用的模型',
    `model_provider_id`   BIGINT                COMMENT '使用的模型供应商ID',
    `prompt_tokens`       INT                   COMMENT '输入token数',
    `completion_tokens`   INT                   COMMENT '输出token数',
    `total_tokens`        INT                   COMMENT '总token数',
    `latency_ms`          INT                   COMMENT '总耗时（毫秒）',
    `status`              TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-成功，1-失败，2-超时',
    `error_msg`           VARCHAR(1024)         COMMENT '错误信息',
    `created_at`          BIGINT                COMMENT '创建时间',
    `updated_at`          BIGINT                COMMENT '更新时间',
    `sort_num`            INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`               INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    KEY `idx_agent_id` (`agent_definition_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_status` (`state`),
    KEY `idx_create_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行记录';

-- =====================================================
-- 8. agent_tool_call_log（工具调用日志）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_tool_call_log` (
    `id`                  BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `run_id`              BIGINT       NOT NULL COMMENT '关联运行记录ID',
    `tool_id`             BIGINT                COMMENT '关联工具ID',
    `agent_definition_id` BIGINT       NOT NULL COMMENT '关联Agent定义ID',
    `request_url`         VARCHAR(512)          COMMENT '实际请求URL',
    `request_method`      VARCHAR(16)           COMMENT '实际请求方法',
    `request_headers`     TEXT                  COMMENT '实际请求头（JSON）',
    `request_body`        TEXT                  COMMENT '实际请求体',
    `response_status`     INT                   COMMENT 'HTTP响应状态码',
    `response_body`       TEXT                  COMMENT '响应体（截断存储，最大64KB）',
    `latency_ms`          INT                   COMMENT '执行耗时（毫秒）',
    `status`              TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-成功，1-失败，2-超时，3-安全拦截',
    `error_msg`           VARCHAR(1024)         COMMENT '错误信息',
    `created_at`          BIGINT                COMMENT '创建时间',
    `updated_at`          BIGINT                COMMENT '更新时间',
    `sort_num`            INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`               INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    KEY `idx_run_id` (`run_id`),
    KEY `idx_tool_id` (`tool_id`),
    KEY `idx_agent_id` (`agent_definition_id`),
    KEY `idx_status` (`state`),
    KEY `idx_create_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具调用日志';

-- =====================================================
-- 9. agent_workflow（工作流 — V0.7预留）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_workflow` (
    `id`                  BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `agent_definition_id` BIGINT       NOT NULL COMMENT '关联Agent定义ID',
    `name`                VARCHAR(64)  NOT NULL COMMENT '工作流名称',
    `description`         VARCHAR(512)          COMMENT '描述',
    `nodes`               LONGTEXT              COMMENT '节点定义（JSON格式，预留）',
    `edges`               LONGTEXT              COMMENT '边定义（JSON格式，预留）',
    `status`              TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-草稿，1-启用，2-禁用',
    `created_at`          BIGINT                COMMENT '创建时间',
    `updated_at`          BIGINT                COMMENT '更新时间',
    `sort_num`            INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`               INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    KEY `idx_agent_id` (`agent_definition_id`),
    KEY `idx_status` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流（V0.7预留）';

-- =====================================================
-- 10. agent_knowledge_base（知识库 — V0.7预留）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_knowledge_base` (
    `id`                  BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `agent_definition_id` BIGINT       NOT NULL COMMENT '关联Agent定义ID',
    `name`                VARCHAR(64)  NOT NULL COMMENT '知识库名称',
    `description`         VARCHAR(512)          COMMENT '描述',
    `index_status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '索引状态：0-未索引，1-索引中，2-已索引',
    `status`              TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `created_at`          BIGINT                COMMENT '创建时间',
    `updated_at`          BIGINT                COMMENT '更新时间',
    `sort_num`            INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`               INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    KEY `idx_agent_id` (`agent_definition_id`),
    KEY `idx_status` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库（V0.7预留）';

-- =====================================================
-- 11. agent_document（文档 — V0.7预留）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_document` (
    `id`                BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `knowledge_base_id` BIGINT       NOT NULL COMMENT '关联知识库ID',
    `title`             VARCHAR(256)          COMMENT '文档标题',
    `content`           LONGTEXT              COMMENT '文档内容（纯文本或Markdown）',
    `source_url`        VARCHAR(512)          COMMENT '来源URL（可选）',
    `chunk_count`       INT                   COMMENT '分块数（预留）',
    `status`            TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-未处理，1-处理中，2-已完成',
    `created_at`        BIGINT                COMMENT '创建时间',
    `updated_at`        BIGINT                COMMENT '更新时间',
    `sort_num`          INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`             INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    KEY `idx_knowledge_base_id` (`knowledge_base_id`),
    KEY `idx_status` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档（V0.7预留）';
