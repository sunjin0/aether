-- v0.7: agent_definition 添加深度思考配置字段
ALTER TABLE `agent_definition`
    ADD COLUMN `default_thinking` TINYINT(1) DEFAULT 0 COMMENT '默认是否启用深度思考' AFTER `max_tool_rounds`,
    ADD COLUMN `default_reasoning_effort` VARCHAR(16) DEFAULT NULL COMMENT '默认推理力度：low/medium/high' AFTER `default_thinking`;
