# PostgreSQL 16 + pgvector 迁移运行手册

## 本地初始化

```powershell
docker compose -f docker-compose.postgresql.yml up -d
docker compose -f docker-compose.postgresql.yml exec postgres pg_isready -U aether -d aether
```

首次创建数据卷时，容器会按文件名顺序执行 `api/src/main/resources/sql/postgresql/001-schema.sql` 和 `api/src/main/resources/sql/postgresql/002-data.sql`。重建初始化环境前，先删除 `aether-postgres-data` 卷。

## 生产切换

1. 在维护窗口开始时停止应用写入，并执行 MySQL 最终备份。
2. 在空的 PostgreSQL 数据库中仅执行 `api/src/main/resources/sql/postgresql/001-schema.sql`；不要执行 `002-data.sql`，避免与生产数据重复。
3. 记录 `tools/migration/verify-mysql-source.sql` 的结果。
4. 使用 pgloader 导入：

```powershell
.\tools\migration\Invoke-PgloaderMigration.ps1 `
  -MySqlDsn 'mysql://USER:PASSWORD@MYSQL_HOST:3306/aether' `
  -PostgresDsn 'postgresql://USER:PASSWORD@POSTGRES_HOST:5432/aether'
```

5. 执行 `tools/migration/verify-postgresql.sql`，逐表比对行数、主键范围、逻辑删除数量和关联完整性。
6. 将应用配置切换到 PostgreSQL，启动 admin/front 并完成登录、权限、Agent 聊天、SSE 和 MCP 工具调用冒烟验证。

## 回滚

保留 MySQL 最终备份和旧库只读访问至少 14 天。切换验证失败时，停止 PostgreSQL 配置的应用，恢复原 MySQL 连接配置并重新启动；不得在回滚后向 PostgreSQL 补写数据。

## 向量基础结构

`agent_document_chunk.embedding` 固定为 `vector(1536)`，对应 OpenAI 兼容的 `text-embedding-3-small`。本次只创建 pgvector 扩展、分块表和 HNSW 余弦索引；不包含文档分块、嵌入生成、检索 API 或 RAG 上下文注入。
