ALTER TABLE agent_tool ADD COLUMN IF NOT EXISTS icon VARCHAR(64);
COMMENT ON COLUMN agent_tool.icon IS '系统图标库名称（Ant Design icon name）';
