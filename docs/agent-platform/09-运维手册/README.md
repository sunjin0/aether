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
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker logs --tail 300 aether-admin
docker logs --tail 300 aether-deep-agent
```

Admin 容器健康检查使用 `/actuator/health`（`admin/src/main/resources/application.yml` 暴露 `health,info`，且 `show-details: never`，返回成功但不泄露组件详情）。该路径无需鉴权：`GlobalFilter` 仅在请求带 `Authorization` 头时才校验令牌。

> 早期版本的本手册曾要求改用 `/v2/api-docs` 并称「勿直接用 Actuator」，此为错误记载：prod profile 下 `knife4j.enable: false`，Springfox 不会 bootstrap，`/v2/api-docs` 实际返回 404。

---

## 生产部署（docker-compose.prod.yml）

仓库共三个 Compose 文件：`docker-compose.yml`（仅 Admin/Front，接入外部基础设施）、`docker-compose.prod.yml`
（生产全栈，见下）、`docker-compose.acceptance.yml`（发布前验收，从本地工作区构建）。

`docker-compose.prod.yml` 相对通用编排的主要加固：

| 维度 | 生产编排的处理 |
|---|---|
| 密钥缺失时 | `${VAR:?}` 直接报错，不启动（不再回落到 `aether_dev` 等开发默认值） |
| 基础设施端口 | PostgreSQL/Redis 不发布宿主端口；MinIO 仅绑回环 |
| 对象存储 | 阿里云 OSS；内置 MinIO 降为 `minio` profile，默认不启动 |
| 应用端口 | 仅 `127.0.0.1`，由同宿主外部网关反代并终止 TLS |
| Redis | `--requirepass` |
| Druid 监控台 | 通过环境变量关闭（默认在 prod 下开启且无鉴权） |
| 日志 | json-file 轮转，20m × 5 |
| 邮件凭据 | 由 `SPRING_MAIL_*` 环境变量覆盖仓库内明文提交的 QQ 邮箱 |
| 私有仓库拉取 | 由 `scripts/fetch-sources.sh` 用宿主机 git 凭据检出后本地构建，不依赖 BuildKit 的 Git 上下文 |

### 首次部署

```sh
cp .env.prod.example .env.prod
# 填写全部 replace-with-* 占位值；随机值用 openssl rand -base64 32 生成
./scripts/fetch-sources.sh                                              # 检出四个源码仓库，并打印提交 SHA
docker compose --env-file .env.prod -f docker-compose.prod.yml config   # 校验，缺失密钥在此报错
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
docker compose --env-file .env.prod -f docker-compose.prod.yml ps       # 全部 healthy
```

`.env.prod` 已在 `.gitignore` 中，切勿提交。

### 发布与回滚

- 源码由 `scripts/fetch-sources.sh` 检出到 `AETHER_SOURCE_ROOT`（默认 `.sources/`），Compose 从该目录构建；发布前把 `AETHER_ADMIN_GIT_REF` 等固定到 tag 或提交 SHA，不要长期停留在 `master`，否则无法复现与回滚。脚本会打印每个仓库实际检出的提交 SHA，应记入发布记录。
- **私有仓库凭据走宿主机 git 配置**。不用 BuildKit 的 Git 构建上下文：其 git 源由构建器自行 clone，不读取宿主机的 `credential.helper`、`~/.git-credentials`、`~/.netrc` 与 SSH 私钥，私有仓库必然拉不下来。改为先由脚本用宿主机凭据检出、再让 Compose 从本地目录构建，既不需要额外令牌，也让凭据回到本来生效的位置。部署前可用 `git ls-remote <仓库地址>` 确认宿主机的 git 能直接拉取。
- `fetch-sources.sh` 默认对每个仓库执行 `git clean -ffdx`，会删除未跟踪文件以保证构建上下文与提交严格一致；`AETHER_SOURCE_ROOT` 下的目录由该脚本专用，不要存放其他内容。加 `--no-clean` 可跳过。
- 回滚即把上述 `*_GIT_REF` 改回上一个已知良好版本后重新 `up -d --build`。数据库回滚见本文档「生产切换/回滚」一节。
- 只有 `admin` 执行 Flyway 迁移（`flyway-core` 仅声明在 `admin/pom.xml`），故 admin 保持单副本；若需多副本，副本必须设 `FLYWAY_ENABLED=false`。`front` 依赖 `admin` 健康后才启动，该依赖不可删除。

### 生产注意事项

- **`SPRING_PROFILES_ACTIVE=prod` 是承重配置**：三个 `application.yml` 默认 profile 均为 `dev`，而 dev profile 硬编码 `localhost` 连接地址，容器内必然失败。
- PostgreSQL 必须使用 pgvector 镜像（`V1__init.sql` 会执行 `CREATE EXTENSION vector`），当前固定为 `pgvector/pgvector:pg16`。
- `sandbox-runner` 是全栈中唯一挂载 `/var/run/docker.sock` 的服务，应部署在专用宿主机上，不得与其他服务共享该 Socket。
- **对象存储默认使用阿里云 OSS**（`STORAGE_PROVIDER=oss`）。各业务共用 `OSS_BUCKET` 这一个 bucket，通过对象键前缀隔离；如需分桶，用 `STORAGE_FILE_BUCKET` / `STORAGE_KNOWLEDGE_BUCKET` / `STORAGE_SKILL_BUCKET` / `STORAGE_ARTIFACT_BUCKET` 覆盖（优先级高于 `OSS_BUCKET`）。建议使用仅授权该 bucket 的 RAM 子账号而非主账号 AK。
- OSS bucket 由服务启动时自动创建，无需手工初始化。`OSS_PUBLIC_ENDPOINT` 仅在绑定了自定义域名时才需填写，否则留空。
- 内置 MinIO 为兜底方案，默认不启动；启用需 `--profile minio` **并同步把 `STORAGE_PROVIDER` 改为 `minio`**。注意 `OSS_*` 在 compose 中是强制校验项，切到 MinIO 时仍须保留非空值——应用在 minio 模式下不会读取它们。
- Java 侧不读取 `OPENAI_API_KEY`/`ANTHROPIC_API_KEY`/`GOOGLE_API_KEY`：模型凭据存放在数据库 `ModelProvider` 表中，仅 Deep Agent 与 MCP 使用环境变量。
- Compose 不内置反向代理：TLS 终止、域名路由、`/druid/**` 兜底封禁均由外部网关负责。

### 待整改（不在 Compose 范围内）

以下问题位于代码/配置层，无法通过 Compose 修复，建议单独排期：

- `common/src/main/java/com/aether/utils/AesUtil.java` 硬编码 AES 密钥，用于封装 Bearer 令牌与加密模型密钥，无法用环境变量轮换。
- `common/src/main/java/com/aether/utils/TokenUtils.java` 硬编码 JWT HMAC 密钥，无环境变量占位，持有源码即可伪造访问令牌。
- `api/src/main/resources/application-prod.yml` 明文提交 SMTP 账号与授权码（dev/test 同样），该凭据已进入 Git 历史，**应予轮换**。
- `api/src/main/resources/application-test.yml` 位于 main resources，会被打进生产 jar。

---

## 数据保留与脱敏

- 工作流终态实例默认保留 90 天：`AETHER_WORKFLOW_SECURITY_RETENTION_DAYS`（0 禁用）。
- 敏感字段脱敏：`AETHER_WORKFLOW_SECURITY_MASK_FIELDS`（默认含 password/secret/token/authorization）。
- 清理 Cron：`AETHER_WORKFLOW_SECURITY_RETENTION_CRON`（默认 `0 30 3 * * ?`）。
