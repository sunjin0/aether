-- Agent message reasoning fields and tool-call log compatibility.
-- Run this migration on databases created before V0.6 reasoning support.

ALTER TABLE `agent_message`
    ADD COLUMN `reasoning_content` LONGTEXT COMMENT '推理内容（assistant角色时）' AFTER `content`,
    ADD COLUMN `reasoning_tokens` INT COMMENT '推理token数' AFTER `total_tokens`;

ALTER TABLE `agent_tool_call_log`
    MODIFY COLUMN `tool_id` BIGINT COMMENT '关联工具ID，工具未匹配时为空';
