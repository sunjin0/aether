# 运维说明

统一部署根目录为 \`/opt/aether-server\`。真实配置仅保存为
\`/opt/aether-server/.env\`，由所有项目发布脚本读取；不要在任一项目目录或发布版本目录创建额外的环境文件。

项目发布目录：

\`\`\`text
/opt/aether-server/aether/releases/<UTC时间>-<tag>
/opt/aether-server/dashboard/releases/<UTC时间>-<tag>
/opt/aether-server/deep-agent/releases/<UTC时间>-<tag>
/opt/aether-server/mcp/releases/<UTC时间>-<tag>
\`\`\`

每个仓库的 \`v*\` 标签只部署自己的服务。发布脚本不执行 \`docker compose down\`、\`--remove-orphans\`，也不重建已有的 PostgreSQL、Redis。

配置变更后，编辑根目录 \`.env\`，然后发布需要重建的项目标签。不要手动改动某一个 release 目录中的配置。
