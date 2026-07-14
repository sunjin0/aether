-- Agent模块字典数据
-- 基于agent-module-dictionaries.md文档
-- 字典表：sys_dict
-- ID策略：从1000开始递增
-- 时间戳：使用当前时间戳（1783769933）
-- 状态：全部启用（state=1）
-- 删除状态：全部未删除（deleted=0）
-- 排序号：父字典为1，子字典按顺序递增

-- =====================================================
-- 1. Agent_Status (工具/供应商状态)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Status', NULL, 'Agent Status', '工具/供应商状态', NULL, NULL, 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Status_Disabled', 'Agent_Status', 'Disabled', '禁用', '0', '禁用状态', 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Status_Enabled', 'Agent_Status', 'Enabled', '启用', '1', '启用状态', 1, b'0', 1783769933, 1783769933, 2);

-- =====================================================
-- 2. Agent_Tool_Type (工具类型)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Tool_Type', NULL, 'Agent Tool Type', '工具类型', NULL, NULL, 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Tool_Type_MCP', 'Agent_Tool_Type', 'MCP', 'MCP', 'mcp', 'MCP 工具', 1, b'0', 1783769933, 1783769933, 1);

-- =====================================================
-- 3. Agent_Mcp_Transport (MCP传输类型)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Mcp_Transport', NULL, 'Agent MCP Transport', 'MCP传输类型', NULL, NULL, 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Mcp_Transport_HTTP', 'Agent_Mcp_Transport', 'HTTP', 'HTTP', 'http', 'HTTP MCP transport', 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Mcp_Transport_SSE', 'Agent_Mcp_Transport', 'SSE', 'SSE', 'sse', 'SSE MCP transport', 1, b'0', 1783769933, 1783769933, 2);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Mcp_Transport_Streamable_HTTP', 'Agent_Mcp_Transport', 'Streamable HTTP', 'Streamable HTTP', 'streamable_http', 'Streamable HTTP MCP transport', 1, b'0', 1783769933, 1783769933, 3);

-- =====================================================
-- 4. Agent_Mcp_Auth_Type (MCP认证类型)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Mcp_Auth_Type', NULL, 'Agent MCP Auth Type', 'MCP认证类型', NULL, NULL, 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Mcp_Auth_Type_None', 'Agent_Mcp_Auth_Type', 'None', '无认证', 'none', '不使用认证', 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Mcp_Auth_Type_Bearer', 'Agent_Mcp_Auth_Type', 'Bearer', 'Bearer Token', 'bearer', 'Bearer Token认证', 1, b'0', 1783769933, 1783769933, 2);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Mcp_Auth_Type_Api_Key', 'Agent_Mcp_Auth_Type', 'API Key', 'API Key', 'api_key', 'API Key认证', 1, b'0', 1783769933, 1783769933, 3);

-- =====================================================
-- 5. Agent_Http_Method (HTTP 请求方法，历史保留)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Http_Method', NULL, 'Agent HTTP Method', 'HTTP 请求方法', NULL, NULL, 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Http_Method_GET', 'Agent_Http_Method', 'GET', 'GET', 'GET', 'GET 请求', 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Http_Method_POST', 'Agent_Http_Method', 'POST', 'POST', 'POST', 'POST 请求', 1, b'0', 1783769933, 1783769933, 2);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Http_Method_PUT', 'Agent_Http_Method', 'PUT', 'PUT', 'PUT', 'PUT 请求', 1, b'0', 1783769933, 1783769933, 3);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Http_Method_DELETE', 'Agent_Http_Method', 'DELETE', 'DELETE', 'DELETE', 'DELETE 请求', 1, b'0', 1783769933, 1783769933, 4);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Http_Method_PATCH', 'Agent_Http_Method', 'PATCH', 'PATCH', 'PATCH', 'PATCH 请求', 1, b'0', 1783769933, 1783769933, 5);

-- =====================================================
-- 6. Agent_Content_Type (Content-Type 类型，历史保留)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Content_Type', NULL, 'Agent Content Type', 'Content-Type 类型', NULL, NULL, 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Content_Type_JSON', 'Agent_Content_Type', 'application/json', 'application/json', 'application/json', 'JSON 格式', 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Content_Type_Form', 'Agent_Content_Type', 'application/x-www-form-urlencoded', 'application/x-www-form-urlencoded', 'application/x-www-form-urlencoded', '表单格式', 1, b'0', 1783769933, 1783769933, 2);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Content_Type_Multipart', 'Agent_Content_Type', 'multipart/form-data', 'multipart/form-data', 'multipart/form-data', '文件上传格式', 1, b'0', 1783769933, 1783769933, 3);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Content_Type_Plain', 'Agent_Content_Type', 'text/plain', 'text/plain', 'text/plain', '纯文本格式', 1, b'0', 1783769933, 1783769933, 4);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Content_Type_XML', 'Agent_Content_Type', 'text/xml', 'text/xml', 'text/xml', 'XML 格式', 1, b'0', 1783769933, 1783769933, 5);

-- =====================================================
-- 7. Agent_Response_Type (响应提取类型，历史保留)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Response_Type', NULL, 'Agent Response Type', '响应提取类型', NULL, NULL, 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Response_Type_JSONPath', 'Agent_Response_Type', 'JSONPath', 'JSONPath', 'jsonpath', 'JSONPath 表达式', 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short() , 'Agent_Response_Type_Regex', 'Agent_Response_Type', 'Regex', '正则', 'regex', '正则表达式', 1, b'0', 1783769933, 1783769933, 2);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Response_Type_Empty', 'Agent_Response_Type', 'Empty', '空（完整响应）', 'empty', '返回完整响应', 1, b'0', 1783769933, 1783769933, 3);

-- =====================================================
-- 8. Model_Provider_Type (供应商类型)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Type', NULL, 'Model Provider Type', '供应商类型', NULL, NULL, 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Type_OpenAI', 'Model_Provider_Type', 'OpenAI', 'OpenAI', 'openai', 'OpenAI 兼容接口', 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Type_Local', 'Model_Provider_Type', 'Local', '本地模型', 'local', '本地模型', 1, b'0', 1783769933, 1783769933, 2);

-- =====================================================
-- 9. Agent_Definition_Status (Agent 定义状态)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Definition_Status', NULL, 'Agent Definition Status', 'Agent 定义状态', NULL, NULL, 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Definition_Status_Draft', 'Agent_Definition_Status', 'Draft', '草稿', '0', '草稿状态', 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Definition_Status_Enabled', 'Agent_Definition_Status', 'Enabled', '启用', '1', '启用状态', 1, b'0', 1783769933, 1783769933, 2);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Definition_Status_Disabled', 'Agent_Definition_Status', 'Disabled', '禁用', '2', '禁用状态', 1, b'0', 1783769933, 1783769933, 3);

-- =====================================================
-- 10. Agent_Access_Type (访问类型)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Access_Type', NULL, 'Agent Access Type', '访问类型', NULL, NULL, 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Access_Type_Private', 'Agent_Access_Type', 'Private', '私有', 'private', '仅自己可访问', 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Access_Type_Public', 'Agent_Access_Type', 'Public', '公开', 'public', '所有人可访问', 1, b'0', 1783769933, 1783769933, 2);

-- =====================================================
-- 11. Agent_Reasoning_Effort (推理力度)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Reasoning_Effort', NULL, 'Agent Reasoning Effort', '推理力度', NULL, NULL, 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Reasoning_Effort_Low', 'Agent_Reasoning_Effort', 'Low', '轻度', 'low', '轻度推理', 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Reasoning_Effort_Medium', 'Agent_Reasoning_Effort', 'Medium', '中度', 'medium', '中度推理', 1, b'0', 1783769933, 1783769933, 2);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Reasoning_Effort_High', 'Agent_Reasoning_Effort', 'High', '深度', 'high', '深度推理', 1, b'0', 1783769933, 1783769933, 3);

-- =====================================================
-- 12. Agent_Run_Status (运行状态)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Run_Status', NULL, 'Agent Run Status', '运行状态', NULL, NULL, 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Run_Status_Success', 'Agent_Run_Status', 'Success', '成功', '0', '运行成功', 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Run_Status_Failed', 'Agent_Run_Status', 'Failed', '失败', '1', '运行失败', 1, b'0', 1783769933, 1783769933, 2);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Run_Status_Timeout', 'Agent_Run_Status', 'Timeout', '超时', '2', '运行超时', 1, b'0', 1783769933, 1783769933, 3);

-- =====================================================
-- 13. Agent_ToolCall_Status (工具调用状态)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_ToolCall_Status', NULL, 'Agent ToolCall Status', '工具调用状态', NULL, NULL, 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_ToolCall_Status_Success', 'Agent_ToolCall_Status', 'Success', '成功', '0', '调用成功', 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_ToolCall_Status_Failed', 'Agent_ToolCall_Status', 'Failed', '失败', '1', '调用失败', 1, b'0', 1783769933, 1783769933, 2);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_ToolCall_Status_Timeout', 'Agent_ToolCall_Status', 'Timeout', '超时', '2', '调用超时', 1, b'0', 1783769933, 1783769933, 3);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_ToolCall_Status_SecurityBlock', 'Agent_ToolCall_Status', 'Security Block', '安全拦截', '3', '安全拦截', 1, b'0', 1783769933, 1783769933, 4);

/*
 * Copyright (c) 2026. Lorem ipsum dolor sit amet, consectetur adipiscing elit.
 * Morbi non lorem porttitor neque feugiat blandit. Ut vitae ipsum eget quam lacinia accumsan.
 * Etiam sed turpis ac ipsum condimentum fringilla. Maecenas magna.
 * Proin dapibus sapien vel ante. Aliquam erat volutpat. Pellentesque sagittis ligula eget metus.
 * Vestibulum commodo. Ut rhoncus gravida arcu.
 */

-- =====================================================
-- 14. Agent_Conversation_Status (会话状态)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Conversation_Status', NULL, 'Agent Conversation Status', '会话状态', NULL, NULL, 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Conversation_Status_Active', 'Agent_Conversation_Status', 'Active', '进行中', '0', '进行中', 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Conversation_Status_Closed', 'Agent_Conversation_Status', 'Closed', '关闭', '1', '已关闭', 1, b'0', 1783769933, 1783769933, 2);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Agent_Conversation_Status_Archived', 'Agent_Conversation_Status', 'Archived', '归档', '2', '已归档', 1, b'0', 1783769933, 1783769933, 3);

-- =====================================================
-- 15. Model_Provider_Name (供应商名称)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Name', NULL, 'Model Provider Name', '供应商名称', NULL, NULL, 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Name_OpenAI', 'Model_Provider_Name', 'OpenAI', 'OpenAI', 'OpenAI', 'OpenAI 官方', 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Name_AzureOpenAI', 'Model_Provider_Name', 'Azure OpenAI', 'Azure OpenAI', 'Azure OpenAI', 'Azure OpenAI 服务', 1, b'0', 1783769933, 1783769933, 2);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Name_Anthropic', 'Model_Provider_Name', 'Anthropic', 'Anthropic', 'Anthropic', 'Claude 系列模型', 1, b'0', 1783769933, 1783769933, 3);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Name_Qwen', 'Model_Provider_Name', '通义千问', '通义千问', 'qwen', '阿里云通义千问', 1, b'0', 1783769933, 1783769933, 4);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Name_Wenxin', 'Model_Provider_Name', '文心一言', '文心一言', 'wenxin', '百度文心一言', 1, b'0', 1783769933, 1783769933, 5);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Name_Zhipu', 'Model_Provider_Name', '智谱 AI', '智谱 AI', 'zhipu', 'GLM 系列模型', 1, b'0', 1783769933, 1783769933, 6);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Name_Local', 'Model_Provider_Name', '本地模型', '本地模型', 'local', '自部署模型', 1, b'0', 1783769933, 1783769933, 7);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Name_Google', 'Model_Provider_Name', 'Google', 'Google', 'google', 'Google Gemini 系列模型', 1, b'0', 1783769933, 1783769933, 8);

-- =====================================================
-- 16. Model_Provider_Model (默认模型)
-- =====================================================
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Model_OpenAI_GPT4o', 'Model_Provider_Name_OpenAI', 'gpt-4o', 'gpt-4o', 'gpt-4o', 'GPT-4o', 1, b'0', 1783769933, 1783769933, 1);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Model_OpenAI_GPT4oMini', 'Model_Provider_Name_OpenAI', 'gpt-4o-mini', 'gpt-4o-mini', 'gpt-4o-mini', 'GPT-4o Mini', 1, b'0', 1783769933, 1783769933, 2);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Model_OpenAI_GPT35Turbo', 'Model_Provider_Name_OpenAI', 'gpt-3.5-turbo', 'gpt-3.5-turbo', 'gpt-3.5-turbo', 'GPT-3.5 Turbo', 1, b'0', 1783769933, 1783769933, 3);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Model_Anthropic_Claude35Sonnet', 'Model_Provider_Name_Anthropic', 'claude-3-5-sonnet', 'claude-3-5-sonnet', 'claude-3-5-sonnet', 'Claude 3.5 Sonnet', 1, b'0', 1783769933, 1783769933, 4);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Model_Anthropic_Claude3Haiku', 'Model_Provider_Name_Anthropic', 'claude-3-haiku', 'claude-3-haiku', 'claude-3-haiku', 'Claude 3 Haiku', 1, b'0', 1783769933, 1783769933, 5);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Model_Qwen_Max', 'Model_Provider_Name_Qwen', 'qwen-max', 'qwen-max', 'qwen-max', '通义千问 Max', 1, b'0', 1783769933, 1783769933, 6);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Model_Qwen_Plus', 'Model_Provider_Name_Qwen', 'qwen-plus', 'qwen-plus', 'qwen-plus', '通义千问 Plus', 1, b'0', 1783769933, 1783769933, 7);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Model_Qwen_Turbo', 'Model_Provider_Name_Qwen', 'qwen-turbo', 'qwen-turbo', 'qwen-turbo', '通义千问 Turbo', 1, b'0', 1783769933, 1783769933, 8);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Model_Wenxin_40', 'Model_Provider_Name_Wenxin', 'ernie-4.0', 'ernie-4.0', 'ernie-4.0', '文心一言 4.0', 1, b'0', 1783769933, 1783769933, 9);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Model_Wenxin_35', 'Model_Provider_Name_Wenxin', 'ernie-3.5', 'ernie-3.5', 'ernie-3.5', '文心一言 3.5', 1, b'0', 1783769933, 1783769933, 10);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Model_Zhipu_GLM4', 'Model_Provider_Name_Zhipu', 'glm-4', 'glm-4', 'glm-4', 'GLM-4', 1, b'0', 1783769933, 1783769933, 11);
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Model_Zhipu_GLM3Turbo', 'Model_Provider_Name_Zhipu', 'glm-3-turbo', 'glm-3-turbo', 'glm-3-turbo', 'GLM-3 Turbo', 1, b'0', 1783769933, 1783769933, 12);
# google/gemma-4-e4b
INSERT INTO `sys_dict` VALUES (uuid_short(), 'Model_Provider_Model_Google_Gemma4E4B', 'Model_Provider_Name_Google', 'gemma-4-e4b', 'gemma-4-e4b', 'google/gemma-4-e4b', 'Gemma 4 E4B', 1, b'0', 1783769933, 1783769933, 13);
