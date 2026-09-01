UPDATE agent_tool tool
SET tenant_id = server.tenant_id
FROM agent_mcp_server server
WHERE tool.tenant_id IS NULL
  AND tool.mcp_server_id = server.id
  AND server.tenant_id IS NOT NULL;
