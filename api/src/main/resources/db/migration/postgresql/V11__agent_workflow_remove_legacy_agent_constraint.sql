-- 工作流已升级为多节点模型，不再绑定单一 Agent；保留历史字段以兼容旧数据。
ALTER TABLE agent_workflow
    ALTER COLUMN agent_definition_id DROP NOT NULL;
