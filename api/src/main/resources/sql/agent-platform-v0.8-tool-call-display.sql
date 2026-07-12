-- v0.8: agent_tool_call_log 增加模型侧调用信息字段
ALTER TABLE `agent_tool_call_log`
    ADD COLUMN `tool_call_id` VARCHAR(128) DEFAULT NULL COMMENT '模型返回的tool call id（如call_xxx）' AFTER `tool_id`,
    ADD COLUMN `tool_name` VARCHAR(128) DEFAULT NULL COMMENT '工具名称' AFTER `tool_call_id`,
    ADD COLUMN `arguments` TEXT COMMENT '模型传给工具的原始参数JSON' AFTER `tool_name`;

-- 索引：同一run内按tool_call_id查询
CREATE INDEX idx_tool_call_log_run_call
ON agent_tool_call_log (run_id, tool_call_id);
