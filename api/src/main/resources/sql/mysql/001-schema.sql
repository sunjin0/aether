-- Aether MySQL schema.
-- Contains table definitions and indexes only; seed/runtime data is in 002-data.sql.

/*
 Navicat Premium Data Transfer

 Source Server         : 本地数据库
 Source Server Type    : MySQL
 Source Server Version : 80036
 Source Host           : localhost:3306
 Source Schema         : demo

 Target Server Type    : MySQL
 Target Server Version : 80036
 File Encoding         : 65001

 Date: 22/07/2025 17:15:36
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for msg_email
-- ----------------------------
DROP TABLE IF EXISTS `msg_email`;
CREATE TABLE `msg_email`  (
                              `id` bigint NOT NULL COMMENT '主键',
                              `user_id` bigint NOT NULL COMMENT '接收用户ID',
                              `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '接收邮箱',
                              `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '消息类型',
                              `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '消息编码',
                              `subject` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '邮件主题',
                              `body` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '邮件内容',
                              `state` int NOT NULL DEFAULT 0 COMMENT '消息状态（0：未读，1：已读）',
                              `created_at` bigint NOT NULL COMMENT '创建时间',
                              `updated_at` bigint NOT NULL COMMENT '修改时间',
                              `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
                              `sort_num` int NOT NULL DEFAULT 1 COMMENT '排序序号',
                              PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统邮件消息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of msg_email
-- ----------------------------

-- ----------------------------
-- Table structure for msg_sms
-- ----------------------------
DROP TABLE IF EXISTS `msg_sms`;
CREATE TABLE `msg_sms`  (
                            `id` bigint NOT NULL COMMENT '主键',
                            `user_id` bigint NOT NULL COMMENT '用户ID',
                            `phone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '手机号码',
                            `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '验证码',
                            `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '验证码类型',
                            `subject` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '主题',
                            `body` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '内容',
                            `state` int NOT NULL DEFAULT 0 COMMENT '状态',
                            `deleted` bit(1) NULL DEFAULT b'0' COMMENT '逻辑删除',
                            `created_at` timestamp(0) NOT NULL COMMENT '创建时间',
                            `updated_at` timestamp(0) NOT NULL COMMENT '更新时间',
                            `sort_num` int NOT NULL DEFAULT 1 COMMENT '排序号',
                            PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统短信消息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of msg_sms
-- ----------------------------

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`  (
                               `id` bigint NOT NULL COMMENT '主键',
                               `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '编码',
                               `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称',
                               `parent` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '父编码',
                               `value` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '值',
                               `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '备注',
                               `state` int NOT NULL DEFAULT 0 COMMENT '状态',
                               `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
                               `created_at` bigint NOT NULL COMMENT '创建时间',
                               `updated_at` bigint NOT NULL COMMENT '修改时间',
                               `sort_num` int NOT NULL DEFAULT 1 COMMENT '排序号',
                               PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_config
-- ----------------------------

-- ----------------------------
-- Table structure for sys_dict
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict`  (
                             `id` bigint NOT NULL COMMENT '主键',
                             `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '编码',
                             `parent` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '父编码',
                             `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称',
                             `name_cn` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '中文名称',
                             `val` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '值',
                             `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
                             `state` int NOT NULL DEFAULT 0 COMMENT '状态',
                             `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
                             `created_at` bigint NOT NULL COMMENT '创建时间',
                             `updated_at` bigint NOT NULL COMMENT '修改时间',
                             `sort_num` int NOT NULL DEFAULT 1 COMMENT '排序号',
                             PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统字典表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dict
-- ----------------------------

-- ----------------------------
-- Table structure for sys_resource
-- ----------------------------
DROP TABLE IF EXISTS `sys_resource`;
CREATE TABLE `sys_resource`  (
                                 `id` bigint NOT NULL COMMENT '主键',
                                 `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '权限名称',
                                 `name_cn` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '权限中文名称',
                                 `path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '权限路径',
                                 `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '权限类型',
                                 `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '权限图标',
                                 `parent_id` bigint NOT NULL COMMENT '父权限ID',
                                 `leaf` bit(1) NULL DEFAULT b'0' COMMENT '叶子节点: true：是 false: f否',
                                 `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '权限描述',
                                 `state` int NOT NULL DEFAULT 0 COMMENT '状态',
                                 `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
                                 `created_at` bigint NOT NULL COMMENT '创建时间',
                                 `updated_at` bigint NOT NULL COMMENT '修改时间',
                                 `sort_num` int NOT NULL DEFAULT 1 COMMENT '排序号',
                                 PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统权限表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_resource
-- ----------------------------


-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
                             `id` bigint NOT NULL COMMENT '主键',
                             `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称',
                             `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
                             `state` int NOT NULL DEFAULT 0 COMMENT '状态',
                             `deleted` bit(1) NULL DEFAULT b'0' COMMENT '逻辑删除',
                             `created_at` bigint NULL DEFAULT NULL COMMENT '创建时间',
                             `updated_at` bigint NULL DEFAULT NULL COMMENT '修改时间',
                             `sort_num` int NULL DEFAULT 1 COMMENT '排序号',
                             PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统角色表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role
-- ----------------------------

-- ----------------------------
-- Table structure for sys_role_resource
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_resource`;
CREATE TABLE `sys_role_resource`  (
                                      `id` bigint NOT NULL COMMENT '主键',
                                      `role_id` bigint NOT NULL COMMENT '角色ID',
                                      `resource_id` bigint NOT NULL COMMENT '权限ID',
                                      `state` int NOT NULL DEFAULT 0 COMMENT '状态',
                                      `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
                                      `created_at` bigint NOT NULL COMMENT '创建时间',
                                      `updated_at` bigint NOT NULL COMMENT '修改时间',
                                      `sort_num` int NOT NULL DEFAULT 1 COMMENT '排序号',
                                      PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统角色权限表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role_resource
-- ----------------------------

-- ----------------------------
-- Table structure for sys_token
-- ----------------------------
DROP TABLE IF EXISTS `sys_token`;
CREATE TABLE `sys_token`  (
                              `id` bigint NOT NULL COMMENT '主键',
                              `user_id` bigint NOT NULL COMMENT '用户ID',
                              `token` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'token',
                              `refresh_token` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '刷新token',
                              `state` int NOT NULL DEFAULT 0 COMMENT '状态',
                              `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
                              `created_at` bigint NOT NULL COMMENT '创建时间',
                              `updated_at` bigint NOT NULL COMMENT '修改时间',
                              `sort_num` int NOT NULL DEFAULT 1 COMMENT '排序号',
                              PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统token表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_token
-- ----------------------------

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                             `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户名',
                             `sex` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '性别',
                             `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户类型',
                             `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邮箱',
                             `phone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '手机号',
                             `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '头像',
                             `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密码',
                             `state` int NOT NULL DEFAULT 0 COMMENT '状态',
                             `deleted` bit(1) NULL DEFAULT b'0' COMMENT '逻辑删除',
                             `created_at` bigint NULL DEFAULT NULL COMMENT '创建时间',
                             `updated_at` bigint NULL DEFAULT NULL COMMENT '修改时间',
                             `sort_num` int NULL DEFAULT 1 COMMENT '排序号',
                             PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1947195431817801730 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user
-- ----------------------------

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
                                  `id` bigint NOT NULL COMMENT '主键',
                                  `user_id` bigint NOT NULL COMMENT '用户ID',
                                  `role_id` bigint NOT NULL COMMENT '角色ID',
                                  `state` int NOT NULL DEFAULT 0 COMMENT '状态',
                                  `deleted` bit(1) NULL DEFAULT b'0' COMMENT '逻辑删除',
                                  `created_at` bigint NULL DEFAULT NULL COMMENT '创建时间',
                                  `updated_at` bigint NULL DEFAULT NULL COMMENT '修改时间',
                                  `sort_num` int NULL DEFAULT 1 COMMENT '排序号',
                                  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统用户角色表' ROW_FORMAT = Dynamic;

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
DROP TABLE IF EXISTS `user_member`;
CREATE TABLE `user_member`  (
                                `id` bigint NOT NULL COMMENT '主键',
                                `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '名称',
                                `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户名',
                                `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '密码',
                                `phone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '电话',
                                `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邮件',
                                `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '头像',
                                `state` bit(1) NULL DEFAULT b'0' COMMENT '状态',
                                `deleted` bit(1) NULL DEFAULT b'0' COMMENT '逻辑删除',
                                `created_at` bigint NULL DEFAULT NULL COMMENT '创建时间',
                                `updated_at` bigint NULL DEFAULT NULL COMMENT '修改时间',
                                `sort_num` int NULL DEFAULT 1 COMMENT '排序号',
                                PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_member
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;

-- Agent platform schema.

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
-- 3. agent_mcp_server（MCP服务）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_mcp_server` (
    `id`                    BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `name`                  VARCHAR(64)  NOT NULL COMMENT 'MCP服务名称',
    `code`                  VARCHAR(64)  NOT NULL COMMENT 'MCP服务编码（唯一）',
    `transport`             VARCHAR(32)  NOT NULL DEFAULT 'http' COMMENT 'MCP传输类型：http/sse/streamable_http',
    `base_url`              VARCHAR(512)          COMMENT 'MCP endpoint',
    `request_headers`       TEXT                  COMMENT 'MCP请求头JSON',
    `auth_type`             VARCHAR(32)  NOT NULL DEFAULT 'none' COMMENT 'MCP认证类型：none/bearer/api_key',
    `auth_token`            VARCHAR(1024)         COMMENT 'MCP认证token（AES加密存储）',
    `command`               VARCHAR(512)          COMMENT 'STDIO命令（预留）',
    `args`                  TEXT                  COMMENT 'STDIO参数JSON（预留）',
    `timeout_ms`            INT          NOT NULL DEFAULT 30000 COMMENT '超时时间（毫秒）',
    `status`                TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `remark`                VARCHAR(512)          COMMENT '备注',
    `created_at`            BIGINT                COMMENT '创建时间',
    `updated_at`            BIGINT                COMMENT '更新时间',
    `sort_num`              INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`               TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`                 INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    UNIQUE KEY `uk_code` (`code`, `deleted`),
    KEY `idx_name` (`name`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP服务';

-- =====================================================
-- 4. agent_tool（工具）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_tool` (
    `id`                    BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `name`                  VARCHAR(64)  NOT NULL COMMENT '工具名称',
    `code`                  VARCHAR(64)  NOT NULL COMMENT '工具编码（唯一）',
    `description`           VARCHAR(512)          COMMENT '描述',
    `tool_type`             VARCHAR(64)           COMMENT '工具业务类型，如 knowledge、ops、dev',
    `mcp_server_id`         BIGINT       NOT NULL COMMENT '关联MCP服务ID',
    `mcp_tool_name`         VARCHAR(128)          COMMENT 'MCP工具名称',
    `mcp_input_schema`      TEXT                  COMMENT 'MCP inputSchema JSON',
    `timeout_ms`            INT          NOT NULL DEFAULT 30000 COMMENT '超时时间（毫秒）',
    `status`                TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `remark`                VARCHAR(512)          COMMENT '备注',
    `created_at`            BIGINT                COMMENT '创建时间',
    `updated_at`            BIGINT                COMMENT '更新时间',
    `sort_num`              INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`               TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`                 INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    UNIQUE KEY `uk_code` (`code`, `deleted`),
    UNIQUE KEY `uk_server_tool` (`mcp_server_id`, `mcp_tool_name`, `deleted`),
    KEY `idx_name` (`name`),
    KEY `idx_tool_type` (`tool_type`),
    KEY `idx_mcp_server_id` (`mcp_server_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具';

-- =====================================================
-- 5. agent_tool_binding（工具绑定）
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
-- 6. agent_conversation（会话）
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
-- 7. agent_message（消息）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_message` (
    `id`                BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `conversation_id`   BIGINT       NOT NULL COMMENT '关联会话ID',
    `role`              VARCHAR(16)  NOT NULL COMMENT '角色：user、assistant、tool',
    `message_type`      VARCHAR(32)  DEFAULT 'chat' COMMENT '消息类型：chat-普通对话，interaction-交互提问，answer-用户回复',
    `interaction_type`  VARCHAR(32)           COMMENT '交互类型：group',
    `interaction_status` VARCHAR(32)          COMMENT '交互状态：pending、answered、cancelled、expired',
    `question_config`   TEXT                  COMMENT '后端校验后的提问配置JSON',
    `parent_message_id` VARCHAR(64)           COMMENT '用户回复关联的提问消息ID',
    `answered_at`       BIGINT                COMMENT '回复时间',
    `expires_at`        BIGINT                COMMENT '过期时间',
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
    KEY `idx_parent_message_id` (`parent_message_id`),
    KEY `idx_interaction_status` (`conversation_id`, `interaction_status`, `deleted`),
    KEY `idx_create_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息';

-- =====================================================
-- 8. agent_run（运行记录）
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
-- 9. agent_tool_call_log（工具调用日志）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_tool_call_log` (
    `id`                  BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `run_id`              BIGINT       NOT NULL COMMENT '关联运行记录ID',
    `tool_id`             BIGINT                COMMENT '关联工具ID',
    `tool_call_id`        VARCHAR(128)          COMMENT '模型返回的tool call id（如call_xxx）',
    `tool_name`           VARCHAR(128)          COMMENT '工具名称',
    `arguments`           TEXT                  COMMENT '模型传给工具的原始参数JSON',
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
    KEY `idx_create_time` (`created_at`),
    KEY `idx_tool_call_log_run_call` (`run_id`, `tool_call_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具调用日志';

-- =====================================================
-- 10. agent_workflow（工作流 — V0.7预留）
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
-- 11. knowledge_base（知识库 — V0.7预留）
-- =====================================================
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id`                  BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `scope`               VARCHAR(16)  NOT NULL DEFAULT 'PLATFORM' COMMENT '知识库范围：PLATFORM-平台级，AGENT-Agent专属',
    `embedding_provider_id` BIGINT              COMMENT 'Embedding模型供应商ID',
    `name`                VARCHAR(64)  NOT NULL COMMENT '知识库名称',
    `description`         VARCHAR(512)          COMMENT '描述',
    `index_status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '索引状态：0-未索引，1-索引中，2-已索引',
    `status`              TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `created_at`          BIGINT                COMMENT '创建时间',
    `updated_at`          BIGINT                COMMENT '更新时间',
    `sort_num`            INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`               INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    KEY `idx_scope` (`scope`),
    KEY `idx_embedding_provider_id` (`embedding_provider_id`),
    KEY `idx_status` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库（V0.7预留）';

-- =====================================================
-- 11.1 agent_knowledge_base_binding（Agent 与知识库绑定）
-- =====================================================
CREATE TABLE IF NOT EXISTS `agent_knowledge_base_binding` (
    `id`                  BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `agent_definition_id` BIGINT       NOT NULL COMMENT '关联Agent定义ID',
    `knowledge_base_id`   BIGINT       NOT NULL COMMENT '关联知识库ID',
    `status`              TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `created_at`          BIGINT                COMMENT '创建时间',
    `updated_at`          BIGINT                COMMENT '更新时间',
    `sort_num`            INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`               INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    UNIQUE KEY `uk_agent_kb` (`agent_definition_id`, `knowledge_base_id`, `deleted`),
    KEY `idx_agent_id` (`agent_definition_id`),
    KEY `idx_kb_id` (`knowledge_base_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 与知识库绑定';

-- =====================================================
-- 12. knowledge_document（文档 — V0.7预留）
-- =====================================================
CREATE TABLE IF NOT EXISTS `knowledge_document` (
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

-- =====================================================
-- 14. knowledge_document_chunk（文档分块，MySQL兼容结构，不支持向量检索）
-- =====================================================
CREATE TABLE IF NOT EXISTS `knowledge_document_chunk` (
    `id`                BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    `knowledge_base_id` BIGINT       NOT NULL COMMENT '关联知识库ID',
    `document_id`       BIGINT       NOT NULL COMMENT '关联文档ID',
    `chunk_index`       INT          NOT NULL COMMENT '分块序号',
    `content`           LONGTEXT     NOT NULL COMMENT '分块文本',
    `token_count`       INT          NOT NULL DEFAULT 0 COMMENT 'Token估算数',
    `embedding`         LONGTEXT     NOT NULL COMMENT '1536维向量文本表示',
    `created_at`        BIGINT                COMMENT '创建时间',
    `updated_at`        BIGINT                COMMENT '更新时间',
    `sort_num`          INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '删除状态：0-未删除，1-已删除',
    `state`             INT          NOT NULL DEFAULT 0 COMMENT '状态 默认0',
    -- MySQL 不支持 PostgreSQL 的部分唯一索引；索引任务会先逻辑删除旧分块，因此此处只保留普通查询索引。
    KEY `idx_knowledge_base` (`knowledge_base_id`, `deleted`),
    KEY `idx_document` (`document_id`, `deleted`, `chunk_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分块（MySQL兼容结构）';

-- 企业知识库扩展。MySQL 保留兼容数据结构，不执行向量检索。
ALTER TABLE `knowledge_base` ADD COLUMN IF NOT EXISTS `owner_admin_id` BIGINT, ADD COLUMN IF NOT EXISTS `visibility` VARCHAR(16) NOT NULL DEFAULT 'platform', ADD COLUMN IF NOT EXISTS `retrieval_config` LONGTEXT, ADD COLUMN IF NOT EXISTS `reference_count` BIGINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS `last_referenced_at` BIGINT;
ALTER TABLE `knowledge_base` ADD COLUMN IF NOT EXISTS `review_config` LONGTEXT;
ALTER TABLE `knowledge_document` ADD COLUMN IF NOT EXISTS `draft_version_id` BIGINT, ADD COLUMN IF NOT EXISTS `submitted_version_id` BIGINT, ADD COLUMN IF NOT EXISTS `review_status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT', ADD COLUMN IF NOT EXISTS `review_updated_at` BIGINT;
ALTER TABLE `knowledge_document` ADD COLUMN IF NOT EXISTS `source_type` VARCHAR(32) NOT NULL DEFAULT 'text', ADD COLUMN IF NOT EXISTS `original_file_name` VARCHAR(512), ADD COLUMN IF NOT EXISTS `file_extension` VARCHAR(32), ADD COLUMN IF NOT EXISTS `mime_type` VARCHAR(128), ADD COLUMN IF NOT EXISTS `file_size` BIGINT, ADD COLUMN IF NOT EXISTS `file_checksum` VARCHAR(128), ADD COLUMN IF NOT EXISTS `storage_bucket` VARCHAR(128), ADD COLUMN IF NOT EXISTS `storage_object_key` VARCHAR(1024), ADD COLUMN IF NOT EXISTS `current_version_no` INT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS `index_status` TINYINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS `parser_type` VARCHAR(64), ADD COLUMN IF NOT EXISTS `index_error_message` LONGTEXT, ADD COLUMN IF NOT EXISTS `indexed_at` BIGINT, ADD COLUMN IF NOT EXISTS `reference_count` BIGINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS `last_referenced_at` BIGINT;
ALTER TABLE `knowledge_document_chunk` ADD COLUMN IF NOT EXISTS `document_version_id` BIGINT, ADD COLUMN IF NOT EXISTS `page_no` INT, ADD COLUMN IF NOT EXISTS `section_path` VARCHAR(512), ADD COLUMN IF NOT EXISTS `content_hash` VARCHAR(128), ADD COLUMN IF NOT EXISTS `metadata` LONGTEXT, ADD COLUMN IF NOT EXISTS `reference_count` BIGINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS `last_referenced_at` BIGINT;
ALTER TABLE `agent_message` ADD COLUMN IF NOT EXISTS `citations` LONGTEXT;
CREATE TABLE IF NOT EXISTS `knowledge_document_version` (`id` BIGINT NOT NULL PRIMARY KEY, `knowledge_document_id` BIGINT NOT NULL, `version_no` INT NOT NULL, `content` LONGTEXT, `storage_bucket` VARCHAR(128), `storage_object_key` VARCHAR(1024), `file_checksum` VARCHAR(128), `parser_type` VARCHAR(64), `index_status` TINYINT NOT NULL DEFAULT 0, `index_error_message` LONGTEXT, `indexed_at` BIGINT, `chunk_count` INT NOT NULL DEFAULT 0, `created_at` BIGINT, `updated_at` BIGINT, `sort_num` INT NOT NULL DEFAULT 0, `deleted` TINYINT NOT NULL DEFAULT 0, `state` INT NOT NULL DEFAULT 0, UNIQUE KEY `uk_doc_version` (`knowledge_document_id`,`version_no`,`deleted`));
ALTER TABLE `knowledge_document_version` ADD COLUMN IF NOT EXISTS `original_content` LONGTEXT, ADD COLUMN IF NOT EXISTS `structured_content` LONGTEXT, ADD COLUMN IF NOT EXISTS `content_checksum` VARCHAR(128), ADD COLUMN IF NOT EXISTS `review_status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT', ADD COLUMN IF NOT EXISTS `source_version_id` BIGINT, ADD COLUMN IF NOT EXISTS `submitted_by` BIGINT, ADD COLUMN IF NOT EXISTS `submitted_at` BIGINT, ADD COLUMN IF NOT EXISTS `reviewed_by` BIGINT, ADD COLUMN IF NOT EXISTS `reviewed_at` BIGINT, ADD COLUMN IF NOT EXISTS `review_comment` LONGTEXT;
CREATE TABLE IF NOT EXISTS `knowledge_review_task` (`id` BIGINT NOT NULL PRIMARY KEY, `knowledge_base_id` BIGINT NOT NULL, `document_id` BIGINT NOT NULL, `document_version_id` BIGINT NOT NULL, `submitter_id` BIGINT NOT NULL, `reviewer_id` BIGINT, `status` VARCHAR(16) NOT NULL, `source_checksum` VARCHAR(128) NOT NULL, `submit_comment` LONGTEXT, `review_comment` LONGTEXT, `submitted_at` BIGINT NOT NULL, `claimed_at` BIGINT, `reviewed_at` BIGINT, `created_at` BIGINT, `updated_at` BIGINT, `sort_num` INT NOT NULL DEFAULT 0, `deleted` TINYINT NOT NULL DEFAULT 0, `state` INT NOT NULL DEFAULT 0, KEY `idx_review_task_assignee` (`reviewer_id`,`status`,`deleted`), KEY `idx_review_task_document` (`document_id`,`submitted_at`,`deleted`));
CREATE TABLE IF NOT EXISTS `knowledge_review_action_log` (`id` BIGINT NOT NULL PRIMARY KEY, `review_task_id` BIGINT, `document_id` BIGINT NOT NULL, `document_version_id` BIGINT NOT NULL, `operator_id` BIGINT, `action` VARCHAR(32) NOT NULL, `before_status` VARCHAR(24), `after_status` VARCHAR(24), `comment` LONGTEXT, `metadata` LONGTEXT, `created_at` BIGINT, `updated_at` BIGINT, `sort_num` INT NOT NULL DEFAULT 0, `deleted` TINYINT NOT NULL DEFAULT 0, `state` INT NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS `knowledge_ai_review` (`id` BIGINT NOT NULL PRIMARY KEY, `knowledge_base_id` BIGINT NOT NULL, `document_id` BIGINT NOT NULL, `document_version_id` BIGINT NOT NULL, `source_checksum` VARCHAR(128) NOT NULL, `model_provider_id` BIGINT, `model` VARCHAR(128), `prompt_version` VARCHAR(32), `status` VARCHAR(16) NOT NULL, `score` INT, `summary` LONGTEXT, `issues` LONGTEXT, `statistics` LONGTEXT, `error_message` LONGTEXT, `started_at` BIGINT, `finished_at` BIGINT, `created_at` BIGINT, `updated_at` BIGINT, `sort_num` INT NOT NULL DEFAULT 0, `deleted` TINYINT NOT NULL DEFAULT 0, `state` INT NOT NULL DEFAULT 0, KEY `idx_ai_review_version` (`document_version_id`,`created_at`,`deleted`));
CREATE TABLE IF NOT EXISTS `knowledge_ai_review_issue` (`id` BIGINT NOT NULL PRIMARY KEY, `ai_review_id` BIGINT NOT NULL, `document_version_id` BIGINT NOT NULL, `block_id` VARCHAR(64), `issue_type` VARCHAR(32) NOT NULL, `severity` VARCHAR(16) NOT NULL, `message` LONGTEXT NOT NULL, `original_excerpt` LONGTEXT, `suggested_patch` LONGTEXT, `handle_status` VARCHAR(16) NOT NULL DEFAULT 'pending', `handled_by` BIGINT, `handled_at` BIGINT, `handle_comment` LONGTEXT, `created_at` BIGINT, `updated_at` BIGINT, `sort_num` INT NOT NULL DEFAULT 0, `deleted` TINYINT NOT NULL DEFAULT 0, `state` INT NOT NULL DEFAULT 0);
UPDATE `knowledge_document_version` SET `review_status` = 'APPROVED' WHERE `index_status` = 2 AND `review_status` = 'DRAFT';
UPDATE `knowledge_document` SET `review_status` = 'APPROVED' WHERE `current_version_no` > 0 AND `review_status` = 'DRAFT';
CREATE TABLE IF NOT EXISTS `knowledge_index_job` (`id` BIGINT NOT NULL PRIMARY KEY, `knowledge_base_id` BIGINT NOT NULL, `document_id` BIGINT NOT NULL, `document_version_id` BIGINT NOT NULL, `job_type` VARCHAR(32) NOT NULL, `status` VARCHAR(16) NOT NULL, `retry_count` INT NOT NULL DEFAULT 0, `max_retry_count` INT NOT NULL DEFAULT 3, `error_message` LONGTEXT, `statistics` LONGTEXT, `started_at` BIGINT, `finished_at` BIGINT, `created_at` BIGINT, `updated_at` BIGINT, `sort_num` INT NOT NULL DEFAULT 0, `deleted` TINYINT NOT NULL DEFAULT 0, `state` INT NOT NULL DEFAULT 0, KEY `idx_kj_status` (`status`,`created_at`));
CREATE TABLE IF NOT EXISTS `knowledge_reference_log` (`id` BIGINT NOT NULL PRIMARY KEY, `agent_definition_id` BIGINT, `conversation_id` BIGINT, `message_id` BIGINT, `knowledge_base_id` BIGINT NOT NULL, `document_id` BIGINT NOT NULL, `document_version_id` BIGINT, `chunk_id` BIGINT NOT NULL, `similarity` DOUBLE, `citation_no` INT, `referenced_at` BIGINT NOT NULL, `created_at` BIGINT, `updated_at` BIGINT, `sort_num` INT NOT NULL DEFAULT 0, `deleted` TINYINT NOT NULL DEFAULT 0, `state` INT NOT NULL DEFAULT 0, KEY `idx_kr_document` (`document_id`,`referenced_at`));

-- =====================================================
-- sys_admin_preference (redesigned)
-- =====================================================
CREATE TABLE IF NOT EXISTS `sys_admin_preference` (
    `id`              BIGINT       NOT NULL PRIMARY KEY COMMENT 'Primary key',
    `admin_id`        BIGINT       NOT NULL COMMENT 'User ID',
    `category`        VARCHAR(32)  NOT NULL COMMENT 'language/style/format/tech_stack/tool_strategy',
    `key_name`        VARCHAR(128) NOT NULL COMMENT 'Preference key',
    `value`           VARCHAR(512) NOT NULL COMMENT 'Preference value',
    `description`     VARCHAR(256)          COMMENT 'Human-readable description',
    `priority`        INT          NOT NULL DEFAULT 50 COMMENT 'Priority 0-100',
    `scope`           VARCHAR(32)  NOT NULL DEFAULT 'global' COMMENT 'global/session/task_type',
    `scope_detail`    VARCHAR(64)           COMMENT 'Task type when scope=task_type',
    `source`          VARCHAR(16)  NOT NULL DEFAULT 'explicit' COMMENT 'explicit/implicit',
    `confidence`      DECIMAL(4,2) NOT NULL DEFAULT 1.00 COMMENT 'Confidence score',
    `usage_count`     INT          NOT NULL DEFAULT 0 COMMENT 'Usage count',
    `last_used_at`    BIGINT                COMMENT 'Last used timestamp',
    `expires_at`      BIGINT                COMMENT 'Expiration time, NULL=never',
    `decay_rate`      DECIMAL(4,2) NOT NULL DEFAULT 0.00 COMMENT 'Daily decay rate',
    `effective_score` DECIMAL(6,2) NOT NULL DEFAULT 100.00 COMMENT 'Current effective score',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '0=disabled 1=enabled',
    `created_at`      BIGINT       NOT NULL COMMENT 'Created timestamp',
    `updated_at`      BIGINT       NOT NULL COMMENT 'Updated timestamp',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT 'Deleted flag',
    KEY `idx_admin_id` (`admin_id`),
    KEY `idx_admin_category` (`admin_id`, `category`),
    KEY `idx_admin_key` (`admin_id`, `key_name`),
    KEY `idx_expires` (`expires_at`),
    KEY `idx_effective` (`admin_id`, `effective_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Admin user preferences';

-- =====================================================
-- sys_admin_preference_event
-- =====================================================
CREATE TABLE IF NOT EXISTS `sys_admin_preference_event` (
    `id`               BIGINT       NOT NULL PRIMARY KEY COMMENT 'Primary key',
    `admin_id`         BIGINT       NOT NULL COMMENT 'User ID',
    `preference_id`    BIGINT                COMMENT 'Related preference ID',
    `event_type`       VARCHAR(16)  NOT NULL COMMENT 'extract/confirm/reject/override/use',
    `category`         VARCHAR(32)           COMMENT 'Extracted category',
    `key_name`         VARCHAR(128)          COMMENT 'Extracted key',
    `value`            VARCHAR(512)          COMMENT 'Extracted value',
    `confidence`       DECIMAL(4,2)          COMMENT 'Confidence score',
    `conversation_id`  BIGINT                COMMENT 'Source conversation',
    `message_id`       BIGINT                COMMENT 'Source message',
    `context_snapshot` TEXT                  COMMENT 'Context summary (JSON)',
    `created_at`       BIGINT       NOT NULL COMMENT 'Created timestamp',
    KEY `idx_admin_id` (`admin_id`),
    KEY `idx_admin_event` (`admin_id`, `event_type`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Preference events log';
