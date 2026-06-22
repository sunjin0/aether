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
INSERT INTO `msg_email` VALUES (1947577954439671809, 1947195431817801729, '2367283463@qq.com', 'Message_Type_Verification', '637566', '登录验证码', '您的验证码是：637566', 0, 1753173743247, 1753174038972, b'0', 1);
INSERT INTO `msg_email` VALUES (1947581264542453762, 1947195431817801729, '2367283463@qq.com', 'Message_Type_Verification', '689726', '登录验证码', '您的验证码是：689726', 2, 1753174532436, 1753174532436, b'0', 1);

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
INSERT INTO `sys_dict` VALUES (3, 'Message_Constant', 'Message', 'Message Constant', '消息常量', NULL, NULL, 1, b'0', 1726196324, 1726196324, 1);
INSERT INTO `sys_dict` VALUES (4, 'Message_Email_From', 'Message_Constant', 'System Mailbox', '系统邮箱', 'sun.summer.day@qq.com', NULL, 1, b'0', 1726196324, 1731994580433, 1);
INSERT INTO `sys_dict` VALUES (5, 'Captcha', NULL, 'Whether the email verification code is enabled', '邮箱验证码是否开启', 'true', 'true，不发送验证码，也不校验', 1, b'0', 1726196324, 1734922204343, 99);
INSERT INTO `sys_dict` VALUES (6, 'Resource_Type', NULL, 'Resource Type', '资源类型', NULL, NULL, 1, b'0', 1726196324, 1726196324, 1);
INSERT INTO `sys_dict` VALUES (7, 'Resource_Type_Route', 'Resource_Type', 'Route', '路由', NULL, NULL, 1, b'0', 1726196324, 1726196324, 1);
INSERT INTO `sys_dict` VALUES (8, 'Resource_Type_Permission', 'Resource_Type', 'Permission', '权限', NULL, NULL, 1, b'0', 1726196324, 1726196324, 1);
INSERT INTO `sys_dict` VALUES (10, 'Gender_Type', NULL, 'Gender Type', '性别类型', NULL, NULL, 1, b'0', 1732157196914, 1732157196914, 1);
INSERT INTO `sys_dict` VALUES (11, 'Gender_Type_Man', 'Gender_Type', 'Man', '男', NULL, NULL, 1, b'0', 1732157246505, 1732157246505, 1);
INSERT INTO `sys_dict` VALUES (12, 'Gender_Type_Woman', 'Gender_Type', 'Woman', '女', '', NULL, 1, b'0', 1732157288146, 1732506856975, 1);
INSERT INTO `sys_dict` VALUES (13, 'Gender_Type_Other', 'Gender_Type', 'Other', '其他', '', NULL, 1, b'0', 1732157317983, 1732157883949, 1);
INSERT INTO `sys_dict` VALUES (14, 'System_Role', NULL, 'System Role', '系统角色', NULL, NULL, 1, b'0', 1732157975145, 1732157975145, 1);
INSERT INTO `sys_dict` VALUES (15, 'System_Role_User', 'System_Role', 'System', '系统', '', NULL, 1, b'0', 1732158021058, 1732162929093, 1);
INSERT INTO `sys_dict` VALUES (16, 'System_Role_Tenant', 'System_Role', 'Tenant', '租户', NULL, NULL, 1, b'0', 1732158093374, 1732158093374, 2);
INSERT INTO `sys_dict` VALUES (22, 'email_template_login_subject', 'email_template', 'Login Verification Code', '登录验证码', '', '登录邮件标题', 1, b'0', 1734682294, 1734682294, 1);
INSERT INTO `sys_dict` VALUES (23, 'email_template_login_content', 'email_template', 'Login Verification Code: ${code}', '您的验证码是：${code}', '', '登录邮件内容', 1, b'0', 1734682294, 1734682294, 1);
INSERT INTO `sys_dict` VALUES (24, 'email_template', NULL, 'Email Template', '邮件模板', '', '邮件模板', 1, b'0', 1734686432, 1734686432, 1);
INSERT INTO `sys_dict` VALUES (28, 'Message', NULL, 'Message Dict', '消息字典', NULL, NULL, 1, b'0', 1734921807446, 1734921807446, 1);
INSERT INTO `sys_dict` VALUES (29, 'Message_Type', 'Message', 'Message Type', '消息类型', NULL, NULL, 1, b'0', 1734922178599, 1734922178599, 0);
INSERT INTO `sys_dict` VALUES (30, 'Message_Type_Notification', 'Message_Type', 'Notification', '通知', NULL, NULL, 1, b'0', 1734922283133, 1734922939573, 1);
INSERT INTO `sys_dict` VALUES (31, 'Message_Type_Verification', 'Message_Type', 'Verification Code', '验证码', NULL, NULL, 1, b'0', 1734922363145, 1734922950949, 1);

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
INSERT INTO `sys_resource` VALUES (1, 'System Management', '系统管理', '/sys', 'Resource_Type_Route', 'SettingOutlined', 0, b'0', NULL, 0, b'0', 1, 1753080218236, 3);
INSERT INTO `sys_resource` VALUES (2, 'Resource Management', '资源管理', '/sys/resource', 'Resource_Type_Route', NULL, 1, b'1', NULL, 0, b'0', 1, 3, 1);
INSERT INTO `sys_resource` VALUES (3, 'Role Management', '角色管理', '/sys/role', 'Resource_Type_Route', NULL, 1, b'1', NULL, 0, b'0', 1, 2, 1);
INSERT INTO `sys_resource` VALUES (4, 'Dictionary Management', '字典管理', '/sys/dict', 'Resource_Type_Route', NULL, 1, b'1', NULL, 0, b'0', 1, 4, 1);
INSERT INTO `sys_resource` VALUES (5, 'Administrator', '管理员', '/sys/admin', 'Resource_Type_Route', NULL, 1, b'1', NULL, 0, b'0', 1, 1, 1);
INSERT INTO `sys_resource` VALUES (10, 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 5, b'1', NULL, 0, b'0', 1, 1, 1);
INSERT INTO `sys_resource` VALUES (11, 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 5, b'1', NULL, 0, b'0', 1, 2, 1);
INSERT INTO `sys_resource` VALUES (12, 'Dashboard', '仪表盘', '/dashboard', 'Resource_Type_Route', 'DashboardOutlined', 0, b'0', '123', 0, b'0', 1, 0, 1);
INSERT INTO `sys_resource` VALUES (13, 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 2, b'1', NULL, 0, b'0', 1, 1, 1);
INSERT INTO `sys_resource` VALUES (14, 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 2, b'1', NULL, 0, b'0', 1, 2, 1);
INSERT INTO `sys_resource` VALUES (15, 'Write', '可写', '', 'Resource_Type_Permission', NULL, 3, b'1', NULL, 0, b'0', 1, 2, 1);
INSERT INTO `sys_resource` VALUES (16, 'Read', '可读', '', 'Resource_Type_Permission', NULL, 3, b'1', NULL, 0, b'0', 1, 1, 1);
INSERT INTO `sys_resource` VALUES (17, 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 4, b'1', NULL, 0, b'0', 1, 1, 1);
INSERT INTO `sys_resource` VALUES (18, 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 4, b'1', NULL, 0, b'0', 1, 2, 1);
INSERT INTO `sys_resource` VALUES (24, 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 23, b'1', NULL, 0, b'0', 1, 1, 1);
INSERT INTO `sys_resource` VALUES (25, 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 23, b'1', NULL, 0, b'0', 1, 1, 1);
INSERT INTO `sys_resource` VALUES (27, 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 21, b'1', NULL, 0, b'0', 0, 1, 1);
INSERT INTO `sys_resource` VALUES (28, 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 21, b'1', NULL, 0, b'0', 0, 1, 1);
INSERT INTO `sys_resource` VALUES (29, 'Message Management', '消息管理', '/msg', 'Resource_Type_Route', 'MessageOutlined', 0, b'0', NULL, 0, b'0', 1, 8, 1);
INSERT INTO `sys_resource` VALUES (30, 'Email Message', '邮箱信息', '/msg/email', 'Resource_Type_Route', NULL, 29, b'1', NULL, 0, b'0', 1, 1, 1);
INSERT INTO `sys_resource` VALUES (31, 'Sms', '手机短信', '/msg/sms', 'Resource_Type_Route', NULL, 29, b'1', NULL, 0, b'0', 1, 2, 1);
INSERT INTO `sys_resource` VALUES (32, 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 30, b'1', NULL, 0, b'0', 1, 1, 1);
INSERT INTO `sys_resource` VALUES (33, 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 30, b'1', NULL, 0, b'0', 1, 1, 1);
INSERT INTO `sys_resource` VALUES (34, 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 31, b'1', NULL, 0, b'0', 1, 1, 1);
INSERT INTO `sys_resource` VALUES (35, 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 31, b'1', NULL, 0, b'0', 1, 1, 1);


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
INSERT INTO `sys_role` VALUES (1, 'root', '超级管理员', 0, b'0', 1, 1753169908348, 1);
INSERT INTO `sys_role` VALUES (1947113915888664578, 's s', NULL, 0, b'1', 1753063107845, 1753063566545, 1);
INSERT INTO `sys_role` VALUES (1947192336782159874, '1', NULL, 0, b'1', 1753081804833, 1753081804833, 1);

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
INSERT INTO `sys_role_resource` VALUES (1187, 1, 10, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1188, 1, 11, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1189, 1, 12, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1190, 1, 13, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1191, 1, 14, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1192, 1, 15, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1193, 1, 16, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1194, 1, 17, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1195, 1, 18, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1196, 1, 32, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1197, 1, 33, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1198, 1, 5, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1199, 1, 2, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1200, 1, 3, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1201, 1, 4, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1202, 1, 30, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1203, 1, 1, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1204, 1, 31, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1205, 1, 34, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1206, 1, 35, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1207, 1, 29, 0, b'1', 1, 1, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411755610113, 1, 10, 0, b'1', 1753066563930, 1753066563930, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718977, 1, 11, 0, b'1', 1753066563933, 1753066563933, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718978, 1, 12, 0, b'1', 1753066563933, 1753066563933, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718979, 1, 13, 0, b'1', 1753066563934, 1753066563934, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718980, 1, 14, 0, b'1', 1753066563934, 1753066563934, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718981, 1, 15, 0, b'1', 1753066563934, 1753066563934, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718982, 1, 16, 0, b'1', 1753066563934, 1753066563934, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718983, 1, 17, 0, b'1', 1753066563934, 1753066563934, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718984, 1, 18, 0, b'1', 1753066563934, 1753066563934, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718985, 1, 32, 0, b'1', 1753066563935, 1753066563935, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718986, 1, 33, 0, b'1', 1753066563935, 1753066563935, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718987, 1, 34, 0, b'1', 1753066563935, 1753066563935, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718988, 1, 35, 0, b'1', 1753066563935, 1753066563935, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718989, 1, 5, 0, b'1', 1753066563935, 1753066563935, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718990, 1, 2, 0, b'1', 1753066563935, 1753066563935, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718991, 1, 3, 0, b'1', 1753066563935, 1753066563935, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718992, 1, 4, 0, b'1', 1753066563936, 1753066563936, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718993, 1, 30, 0, b'1', 1753066563936, 1753066563936, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718994, 1, 31, 0, b'1', 1753066563936, 1753066563936, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718995, 1, 1, 0, b'1', 1753066563936, 1753066563936, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718996, 1, 29, 0, b'1', 1753066563936, 1753066563936, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718997, 1, 6, 0, b'1', 1753066563936, 1753066563936, 1);
INSERT INTO `sys_role_resource` VALUES (1947128411822718998, 1, 9, 0, b'1', 1753066563937, 1753066563937, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292034, 1, 10, 0, b'1', 1753066571527, 1753066571527, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292035, 1, 11, 0, b'1', 1753066571528, 1753066571528, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292036, 1, 13, 0, b'1', 1753066571528, 1753066571528, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292037, 1, 14, 0, b'1', 1753066571528, 1753066571528, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292038, 1, 15, 0, b'1', 1753066571529, 1753066571529, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292039, 1, 16, 0, b'1', 1753066571529, 1753066571529, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292040, 1, 17, 0, b'1', 1753066571529, 1753066571529, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292041, 1, 18, 0, b'1', 1753066571529, 1753066571529, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292042, 1, 32, 0, b'1', 1753066571529, 1753066571529, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292043, 1, 33, 0, b'1', 1753066571530, 1753066571530, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292044, 1, 34, 0, b'1', 1753066571530, 1753066571530, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292045, 1, 35, 0, b'1', 1753066571530, 1753066571530, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292046, 1, 9, 0, b'1', 1753066571530, 1753066571530, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292047, 1, 5, 0, b'1', 1753066571530, 1753066571530, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292048, 1, 2, 0, b'1', 1753066571530, 1753066571530, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292049, 1, 3, 0, b'1', 1753066571531, 1753066571531, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292050, 1, 4, 0, b'1', 1753066571531, 1753066571531, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292051, 1, 30, 0, b'1', 1753066571531, 1753066571531, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292052, 1, 31, 0, b'1', 1753066571531, 1753066571531, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292053, 1, 1, 0, b'1', 1753066571531, 1753066571531, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292054, 1, 6, 0, b'1', 1753066571531, 1753066571531, 1);
INSERT INTO `sys_role_resource` VALUES (1947128443653292055, 1, 29, 0, b'1', 1753066571532, 1753066571532, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152002, 1, 10, 0, b'1', 1753066695740, 1753066695740, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152003, 1, 11, 0, b'1', 1753066695741, 1753066695741, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152004, 1, 13, 0, b'1', 1753066695742, 1753066695742, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152005, 1, 14, 0, b'1', 1753066695742, 1753066695742, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152006, 1, 15, 0, b'1', 1753066695742, 1753066695742, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152007, 1, 16, 0, b'1', 1753066695742, 1753066695742, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152008, 1, 17, 0, b'1', 1753066695743, 1753066695743, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152009, 1, 18, 0, b'1', 1753066695743, 1753066695743, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152010, 1, 32, 0, b'1', 1753066695743, 1753066695743, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152011, 1, 33, 0, b'1', 1753066695743, 1753066695743, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152012, 1, 34, 0, b'1', 1753066695744, 1753066695744, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152013, 1, 35, 0, b'1', 1753066695744, 1753066695744, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152014, 1, 5, 0, b'1', 1753066695744, 1753066695744, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152015, 1, 2, 0, b'1', 1753066695745, 1753066695745, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152016, 1, 3, 0, b'1', 1753066695745, 1753066695745, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152017, 1, 4, 0, b'1', 1753066695745, 1753066695745, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152018, 1, 30, 0, b'1', 1753066695746, 1753066695746, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152019, 1, 31, 0, b'1', 1753066695746, 1753066695746, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152020, 1, 1, 0, b'1', 1753066695746, 1753066695746, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152021, 1, 29, 0, b'1', 1753066695746, 1753066695746, 1);
INSERT INTO `sys_role_resource` VALUES (1947128964657152022, 1, 12, 0, b'1', 1753066695746, 1753066695746, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775361, 1, 10, 0, b'1', 1753066726710, 1753066726710, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775362, 1, 11, 0, b'1', 1753066726710, 1753066726710, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775363, 1, 13, 0, b'1', 1753066726711, 1753066726711, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775364, 1, 14, 0, b'1', 1753066726711, 1753066726711, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775365, 1, 15, 0, b'1', 1753066726711, 1753066726711, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775366, 1, 16, 0, b'1', 1753066726711, 1753066726711, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775367, 1, 17, 0, b'1', 1753066726711, 1753066726711, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775368, 1, 18, 0, b'1', 1753066726712, 1753066726712, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775369, 1, 32, 0, b'1', 1753066726712, 1753066726712, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775370, 1, 33, 0, b'1', 1753066726712, 1753066726712, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775371, 1, 34, 0, b'1', 1753066726712, 1753066726712, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775372, 1, 35, 0, b'1', 1753066726712, 1753066726712, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775373, 1, 5, 0, b'1', 1753066726713, 1753066726713, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775374, 1, 2, 0, b'1', 1753066726713, 1753066726713, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775375, 1, 3, 0, b'1', 1753066726713, 1753066726713, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775376, 1, 4, 0, b'1', 1753066726714, 1753066726714, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775377, 1, 30, 0, b'1', 1753066726714, 1753066726714, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775378, 1, 31, 0, b'1', 1753066726714, 1753066726714, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775379, 1, 1, 0, b'1', 1753066726715, 1753066726715, 1);
INSERT INTO `sys_role_resource` VALUES (1947129094533775380, 1, 29, 0, b'1', 1753066726715, 1753066726715, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659102502914, 1, 10, 0, b'1', 1753081404842, 1753081404842, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659102502915, 1, 11, 0, b'1', 1753081404849, 1753081404849, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659102502916, 1, 13, 0, b'1', 1753081404850, 1753081404850, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659102502917, 1, 14, 0, b'1', 1753081404850, 1753081404850, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659102502918, 1, 15, 0, b'1', 1753081404851, 1753081404851, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659102502919, 1, 16, 0, b'1', 1753081404851, 1753081404851, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659102502920, 1, 32, 0, b'1', 1753081404852, 1753081404852, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659152834561, 1, 33, 0, b'1', 1753081404852, 1753081404852, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659152834562, 1, 34, 0, b'1', 1753081404852, 1753081404852, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659152834563, 1, 35, 0, b'1', 1753081404852, 1753081404852, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659152834564, 1, 5, 0, b'1', 1753081404853, 1753081404853, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659152834565, 1, 2, 0, b'1', 1753081404853, 1753081404853, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659152834566, 1, 3, 0, b'1', 1753081404853, 1753081404853, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659152834567, 1, 30, 0, b'1', 1753081404854, 1753081404854, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659152834568, 1, 31, 0, b'1', 1753081404854, 1753081404854, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659152834569, 1, 29, 0, b'1', 1753081404854, 1753081404854, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659152834570, 1, 17, 0, b'1', 1753081404855, 1753081404855, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659152834571, 1, 4, 0, b'1', 1753081404855, 1753081404855, 1);
INSERT INTO `sys_role_resource` VALUES (1947190659152834572, 1, 1, 0, b'1', 1753081404856, 1753081404856, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038465, 1, 10, 0, b'0', 1753081608512, 1753081608512, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038466, 1, 11, 0, b'0', 1753081608513, 1753081608513, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038467, 1, 13, 0, b'0', 1753081608513, 1753081608513, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038468, 1, 14, 0, b'0', 1753081608513, 1753081608513, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038469, 1, 15, 0, b'0', 1753081608514, 1753081608514, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038470, 1, 16, 0, b'0', 1753081608514, 1753081608514, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038471, 1, 32, 0, b'0', 1753081608514, 1753081608514, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038472, 1, 33, 0, b'0', 1753081608514, 1753081608514, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038473, 1, 34, 0, b'0', 1753081608514, 1753081608514, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038474, 1, 35, 0, b'0', 1753081608515, 1753081608515, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038475, 1, 17, 0, b'0', 1753081608515, 1753081608515, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038476, 1, 5, 0, b'0', 1753081608515, 1753081608515, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038477, 1, 2, 0, b'0', 1753081608515, 1753081608515, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038478, 1, 3, 0, b'0', 1753081608515, 1753081608515, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038479, 1, 30, 0, b'0', 1753081608516, 1753081608516, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038480, 1, 31, 0, b'0', 1753081608516, 1753081608516, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038481, 1, 29, 0, b'0', 1753081608516, 1753081608516, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038482, 1, 1, 0, b'0', 1753081608516, 1753081608516, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038483, 1, 4, 0, b'0', 1753081608516, 1753081608516, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038484, 1, 18, 0, b'0', 1753081608517, 1753081608517, 1);
INSERT INTO `sys_role_resource` VALUES (1947191513327038485, 1, 12, 0, b'0', 1753081608517, 1753081608517, 1);
INSERT INTO `sys_role_resource` VALUES (1947192354003972097, 1947192336782159874, 29, 0, b'0', 1753081808945, 1753081808945, 1);
INSERT INTO `sys_role_resource` VALUES (1947192354003972098, 1947192336782159874, 30, 0, b'0', 1753081808946, 1753081808946, 1);
INSERT INTO `sys_role_resource` VALUES (1947192354003972099, 1947192336782159874, 31, 0, b'0', 1753081808946, 1753081808946, 1);
INSERT INTO `sys_role_resource` VALUES (1947192354003972100, 1947192336782159874, 32, 0, b'0', 1753081808946, 1753081808946, 1);
INSERT INTO `sys_role_resource` VALUES (1947192354003972101, 1947192336782159874, 33, 0, b'0', 1753081808946, 1753081808946, 1);
INSERT INTO `sys_role_resource` VALUES (1947192354003972102, 1947192336782159874, 34, 0, b'0', 1753081808946, 1753081808946, 1);
INSERT INTO `sys_role_resource` VALUES (1947192354003972103, 1947192336782159874, 35, 0, b'0', 1753081808946, 1753081808946, 1);

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
INSERT INTO `sys_token` VALUES (1945313010319089666, 1945059543981625345, 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeAtbkhM6/1qAf1oGpyu9lku9L7ujLLQ5Ky1M3k7o5pYWlXJDWFZ2FSoBOA1aociTmCh9k2yd/I2dUG6Xe4IJ0oBvTS/JP+1j+gSTI6At6jbgRzL6F0xasdnEJJ5j4fkFwCu6yf/nyLzIjhEiIlIpMF8', 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeADUQC4uViUNROj+q6SPzvg9QE5mFhSsxhwYHN0m565ovKQfvrLlePSMK4TXx2THDrYnlc0BZNkwgkfsrw8fxrZa/1ctC7dGl3cwxNDcxAOunKhy0vRsp1CMqPQzcS1h6DG5PAcZndGAW39FcGuw57M', 1, b'1', 1752633738486, 1753088815462, 1);
INSERT INTO `sys_token` VALUES (1947222178596630529, 1945059543981625345, 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeCeNHqZzVbPCSnSRKlGZGOeyGmXUJd2WsDrxv2Ao5QvQfDWptPo2+k4KUyD7pB9UdUUU8aL46YFHndleRzDiv6fsXjr0qCP0EGrp0QOxf5kD3A8QdTsvSgrGFLCF7IMC1d/KtBFyJUVDpIuQuPGnt6A', 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeBKoszhuGCME1OKONwk8dMBMRGY5zK7AGSwVCfr0i8xC3ghRDvO7wPORk7TgsAqEuK4Z65TrM+jWIE0k0xb3lDmaI6gUs5Z1Gsy1QiK15l3ZviLjTJJviCT8x7Vb+q+EtPSi/yEPk3Nw/6Ql6eepW/3', 1, b'1', 1753088919676, 1753089640109, 1);
INSERT INTO `sys_token` VALUES (1947229725294473217, 1945059543981625345, 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeDmEusjmEfqqvOiJP4zJzfdpdNLLJhYSOW8bpcmUbouBOCTXuhGswBUYdq55OAycC9nJkOGcCaTdxmBNIB2X1K2f+GvXwPdRMrR/sd18R4JhR9atQVIJyi/WUJgCcL+Pw4Xt5KylVPghKdyDXBQSJ5E', 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeAsFK2/3RopEMxLaebU5NnwkKkKaXH/tdsmXZZLdaUblHL++EpNCRUNWyKNK2zguCgdqk1VSADh8cTVRFxbXEwjfqDigQGl7eOKS5bI62MZX7he6iV+FnUtbpKLu3tF7aRWsKCrBOM6SdpA3Ymz0cio', 1, b'1', 1753090718947, 1753090765014, 1);
INSERT INTO `sys_token` VALUES (1947230017838788610, 1945059543981625345, 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeBNctHJsxw2J32sZcgJNEYkK7npaC5JGLtoOTYrXRgLLxPLyTgfJ+a0qSRRz4eLqyvswxfuDD2O01GyDz/9iRkAt+BL+kVIjtSK+l7lB2wThpsIIQ2Nar4zd8MiIjHrdpDKP6IZ/CgBrLMJU+Gvy5QM', 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeCRtSVX77Vl8cWV/KyHY6UvDapLMz5XoIcLhxqZq8AyRcjnPDDFVRiRz5Si6ByLiGdGAuyb2HVstxNENENeu1d1w18Ow1GbTlyXCgd5Z5uO9O8LJcTYbsRFCV1cV6epBfsT/iIcG0tSKF3onVBzRgto', 1, b'0', 1753090788700, 1753150333164, 1);
INSERT INTO `sys_token` VALUES (1947501622221586433, 1947195431817801729, 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeB+RnA/QoqZFBYY/IR8aRzaM0fXe8uX+3EGbvHfC61cYy01XnjklHghCYUcdWbKL6GwcE4eeqVExiIfoLY6AxK//2hjz8MHOVOnglO8NhNkjXaladn9rcrOXAqUeFJODQ20sHT6O7pUiEQWi0iz91j2', 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeDxWyCTzMmsrpJFS1ebtvROwf6cfmW2v4VEM7k7y5SYTcA4iQHtEemsKmlZCIL1LcBbhdYpH9tnJMRof+z7BORn/3M9yoRG6y4AXQA6KspFqvY/XwmG0jXzoCYv+o/V3plqOolSktL2OZt7LNACW/BN', 1, b'1', 1753155544226, 1753156181682, 1);
INSERT INTO `sys_token` VALUES (1947578153694277633, 1947195431817801729, 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeCAO4V1WxJKX+4mbY141Ameh2ouUGqSDG2xZkQ5Hjd+vxSu67Ij6ntpOxhb3Ztrj50Ze0y4H0G/lXgdRMPA2y7Rf+F3oC56klEVEJsNpshwodVP/jllVA9xH+S8yA5DPuIpYpr6Oohv2m10ctU1z+pS', 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeBRlAjEA5f/6WKsLcJk3m8NMG0jkjkLEiXaRh/G3eK4e4DBV64+2IxucsFpntC0Xc/69R16Zqus5YGBtsjdiJCsbtOTG1/z50XBK7q+ItiwyHs10WdpEjs52pRNXqh80EoDxXCZL0Xc/Xxu00X7stK5', 1, b'1', 1753173790750, 1753173790743, 1);
INSERT INTO `sys_token` VALUES (1947581343307288577, 1947195431817801729, 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeBYTApVtCbSaDHPxmUhUz7e8Lw1/1e7lpkaeg6WbU+g5vyH+9vbvoIxkCVewL/f6x/+k+Qx01u7VAcK0wXQeahO0HXXCthofODYMlPU/hwoDlS+1yB+FrbVfDLkwLPvGubNLJz/JQd4heEEhOgIrjHB', 'gEOatMmTopBKm1wE59YnchNzD7geIK0figeXX1o+nuaS+e4nGldgUUYLH0CV6Qc8fPI032Pa6BVIzEpn6eBcVYZ4wf92PtEp4SILN7xvDeAaVzEgv6A9HKfwvWIIrqev6C/vEq2dMnwKdmRXSKTTGAWi9qaHIWnwG+MnWEkBoM1fo2D5v/Q5Vnir2QlqZZeuHRfX2cqpCaONY+tSIcm3fyNM+bsBkJzdALOueoG8GBgujJKE3CPhx8aRNWoW5qYn', 1, b'0', 1753174551217, 1753174551212, 1);

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
INSERT INTO `sys_user` VALUES (1945059543981625345, 'admin', 'Gender_Type_Man', 'System_Role_User', 'admin@163.com', '1345634565', 'https://gw.alipayobjects.com/zos/antfincdn/XAosXuNZyF/BiazfanxmamNRoxxVxka.png', '$2a$10$sAzNmHYKebbZ9BqHE.2PeeIDLTAMCjgtFot7t9wgNvfu35S9/Ouhu', 0, b'0', 1752573307404, 1753081835834, 1);
INSERT INTO `sys_user` VALUES (1947195431817801729, 'manger', 'Gender_Type_Woman', 'System_Role_User', '2367283463@qq.com', '64135511', 'https://gw.alipayobjects.com/zos/antfincdn/XAosXuNZyF/BiazfanxmamNRoxxVxka.png', '$2a$10$uQG.Gd2AAp6J9.vBtctRNuYtg32IrCbxewgKUTcotCJLFpRpdLMdK', 0, b'0', 1753082542754, 1753085765290, 1);

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
INSERT INTO `sys_user_role` VALUES (1, 1945059543981625345, 1, 0, b'1', NULL, NULL, 1);
INSERT INTO `sys_user_role` VALUES (1947181834177753090, 1945059543981625345, 1, 0, b'1', 1753079300817, 1753079300817, 1);
INSERT INTO `sys_user_role` VALUES (1947192406902534145, 1945059543981625345, 1, 0, b'1', 1753081821554, 1753081821554, 1);
INSERT INTO `sys_user_role` VALUES (1947192416658485249, 1945059543981625345, 1, 0, b'1', 1753081823881, 1753081823881, 1);
INSERT INTO `sys_user_role` VALUES (1947192427626590209, 1945059543981625345, 1, 0, b'1', 1753081826496, 1753081826496, 1);
INSERT INTO `sys_user_role` VALUES (1947192449176924162, 1945059543981625345, 1, 0, b'1', 1753081831629, 1753081831629, 1);
INSERT INTO `sys_user_role` VALUES (1947192458173706242, 1945059543981625345, 1, 0, b'1', 1753081833780, 1753081833780, 1);
INSERT INTO `sys_user_role` VALUES (1947192466784612354, 1945059543981625345, 1, 0, b'0', 1753081835832, 1753081835832, 1);
INSERT INTO `sys_user_role` VALUES (1947195431880716290, 1947195431817801729, 1, 0, b'1', 1753082542764, 1753082542764, 1);
INSERT INTO `sys_user_role` VALUES (1947208948109242370, 1947195431817801729, 1, 0, b'0', 1753085765286, 1753085765286, 1);

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
