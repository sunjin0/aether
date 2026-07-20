# Agent 平台 — 运维手册

> 原文档：POSTGRES_MIGRATION.md
> 更新日期：2026-07-20

---

## PostgreSQL 16 + pgvector 迁移

### 本地初始化

```powershell
docker compose -f docker-compose.postgresql.yml up -d
docker compose -f docker-compose.postgresql.yml exec postgres pg_isready -U aether -d aether
```

### 生产切换步骤

1. **维护窗口**：停止写入，对 MySQL 做完整备份
2. **建表**：执行 `api/src/main/resources/sql/postgresql/001-schema.sql`
3. **数据导入**：使用 pgloader 从 MySQL 导入
4. **验证**：检查数据完整性（行数对比、关键字段抽样）
5. **切换**：修改配置指向 PostgreSQL，启动应用冒烟验证

### 回滚方案

- 保留 MySQL 备份至少 14 天
- 切换失败时恢复旧配置指向 MySQL

### 向量基础结构

- `knowledge_document_chunk.embedding`：固定 `vector(1536)`
- 对应模型：`text-embedding-3-small`
- 索引：HNSW
- 扩展：`CREATE EXTENSION vector`
