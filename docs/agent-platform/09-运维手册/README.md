# Agent 平台 — 运维手册

> 更新日期：2026-08-04

---

## 数据库与 Flyway 迁移

建表与数据迁移全部由 Flyway 管理，迁移脚本位于 `api/src/main/resources/db/migration/postgresql/`（V1__init.sql ~ V37）：

- `V1__init.sql`：完整初始化（建表 + 种子数据 + 公共索引 + pgvector）。
- `V2`~`V37`：增量变更（会话摘要字段、Deep Agent、混合检索、检索评测、服务账号、工作流运行时、触发器、菜单/权限种子及评测可靠性增强）。

Admin/Front 启动时自动执行迁移；相关配置见 `admin/src/main/resources/application.yml` 的 `spring.flyway`
块（locations、baseline-on-migrate、validate-on-migrate）。

### 本地初始化（PostgreSQL 16 + pgvector）

```powershell
docker compose -f docker-compose.postgresql.yml up -d
docker compose -f docker-compose.postgresql.yml exec postgres pg_isready -U aether -d aether
mvn -pl admin -am -DskipTests install
mvn -pl admin org.springframework.boot:spring-boot-maven-plugin:2.7.18:run -Dspring-boot.run.profiles=dev
```

### 向量基础结构

- `knowledge_document_chunk.embedding`：固定 `vector(1536)`，对应 `text-embedding-3-small`。
- 索引：HNSW 余弦（`vector_cosine_ops`）+ 词法 GIN（`to_tsvector('simple', content)`）。
- 扩展：`CREATE EXTENSION vector`（V1 内自动执行）。

### 生产切换/回滚

- 维护窗口内对旧库做完整备份，通过 pgloader 导入数据到空库后再启动应用（Flyway baseline）。
- 保留旧库备份至少 14 天；切换失败时恢复旧配置指向旧库。
- `FLYWAY_ENABLED=false` 可关闭自动迁移（仅限完全受控的部署）。

---

## 应用配置要点

### Deep Agent 集成（生产）

```env
AETHER_DEEP_AGENT_BASE_URL=
AETHER_DEEP_AGENT_SHARED_SECRET=
AETHER_DEEP_AGENT_KEY_ID=deep-agent-v1
AETHER_MCP_DELEGATION_SECRET=
AETHER_DEEP_AGENT_RUN_TIMEOUT_SECONDS=600
```

### 业务工作流回调（默认关闭）

```env
AETHER_WORKFLOW_CALLBACK_ENABLED=true
AETHER_WORKFLOW_CALLBACK_ALLOWED_HOSTS=workflow.example.com
AETHER_WORKFLOW_CALLBACK_SIGNING_SECRET=replace-with-a-long-random-secret
```

### 服务账号令牌

```env
AETHER_SERVICE_ACCOUNT_ACCESS_TOKEN_SECONDS=900   # 最大 3600
```

### 存储

```env
MINIO_ENDPOINT= MINIO_PUBLIC_ENDPOINT= MINIO_ACCESS_KEY= MINIO_SECRET_KEY=
```

### 文档解析

`docling.service.url`（默认 `http://127.0.0.1:8000`）用于 PDF/DOCX 结构化解析与 XLSX 导入；未配置时退化为内置解析。

---

## 常用检查

```powershell
docker compose -f docker-compose.all.yml -p aether ps
docker logs --tail 300 aether-admin
docker logs --tail 300 aether-deep-agent
```

Admin 容器健康检查使用 `/v2/api-docs`（项目为 Springfox 2.x，勿在兼容改造前直接用 Actuator）。

---

## 数据保留与脱敏

- 工作流终态实例默认保留 90 天：`AETHER_WORKFLOW_SECURITY_RETENTION_DAYS`（0 禁用）。
- 敏感字段脱敏：`AETHER_WORKFLOW_SECURITY_MASK_FIELDS`（默认含 password/secret/token/authorization）。
- 清理 Cron：`AETHER_WORKFLOW_SECURITY_RETENTION_CRON`（默认 `0 30 3 * * ?`）。
