# Aether Admin

Aether 的 Java 后端与平台聚合部署项目。提供用户与权限、Agent 配置、普通 Agent 聊天、Deep Agent 运行编排、MCP
工具审批、知识库检索和文件存储能力。

## 模块

| 模块        | 说明                                   |
|-----------|--------------------------------------|
| `common`  | 通用响应、认证、权限、Redis、异常和国际化。             |
| `api`     | 实体、VO、Mapper、服务接口、数据库迁移与国际化资源。       |
| `storage` | MinIO 等对象存储适配。                       |
| `biz`     | Agent、知识库、用户与业务实现。                   |
| `admin`   | Spring Boot 管理/API 应用，默认容器端口 `8080`。 |
| `front`   | 前端应用壳模块。                             |

## 技术与依赖

- Java 8 源码目标，Spring Boot 2.7.18，Maven 多模块构建。
- PostgreSQL（建议 pgvector）、Redis、MinIO。
- 普通 Agent 直接通过模型供应商的 OpenAI 兼容接口流式回复。
- Deep Agent 由 `aether-deep-agent-service` 执行；Java 负责运行生命周期、HMAC 回调校验、工具审批和短期 MCP 委派 JWT。

## 本地开发

准备 PostgreSQL、Redis、MinIO 后，在项目根目录执行：

```powershell
mvn -pl admin -am -DskipTests install
mvn -pl admin org.springframework.boot:spring-boot-maven-plugin:2.7.18:run -Dspring-boot.run.profiles=dev
```

常用验证命令：

```powershell
mvn -pl admin -am -DskipTests compile
mvn -pl biz -am test
```

生产环境主要变量见 `.env.example`：`DB_URL`、`REDIS_HOST`、`MINIO_*`、`AETHER_DEEP_AGENT_*` 与
`AETHER_MCP_DELEGATION_SECRET`。普通聊天默认关闭查询重写，避免在主模型调用前增加一次同步模型请求。

## Docker 部署

仓库提供两个 Compose 文件：

| 文件 | 用途 |
|---|---|
| `docker-compose.yml` | 仅 Admin 与 Front，接入已存在的外部网络与基础设施 |
| `docker-compose.prod.yml` | 生产全栈：PostgreSQL、Redis、Admin、Front、Dashboard、Deep Agent、MCP 与 Sandbox |
| `docker-compose.acceptance.yml` | 发布前验收：四个应用服务从本地工作区构建，覆盖未推送的改动 |

生产全栈部署（对象存储默认使用阿里云 OSS，内置 MinIO 为 `--profile minio` 兜底）：

```powershell
Copy-Item .env.prod.example .env.prod
# 编辑 .env.prod：OSS 凭据、数据库与 Redis 密码、SMTP、Deep Agent 与 MCP 委派密钥
./scripts/fetch-sources.sh                                              # 用部署机 git 凭据检出四个仓库
docker compose --env-file .env.prod -f docker-compose.prod.yml config   # 校验，缺失密钥在此报错
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

业务服务源码由 `scripts/fetch-sources.sh` 用部署机已配置的 git 凭据检出到 `.sources/`，Compose 再从本地目录构建；
发布前应把 `AETHER_ADMIN_GIT_REF` 等固定到 tag 或提交 SHA，脚本会打印每个仓库实际使用的提交 SHA。

不直接使用 Compose 的 Git 构建上下文，是因为 BuildKit 的 git 源由构建器自行 clone，**不读取宿主机 git 配置**
（`credential.helper`、`~/.git-credentials`、`~/.netrc`、SSH 私钥均不生效），私有仓库拉不下来；先检出再本地构建
可以让凭据留在它本来生效的地方，也无需额外维护访问令牌。

默认宿主机端口为 Admin `8080`、Front `8081`、Dashboard `8001`、MCP `8000`、Deep Agent `8010`、MinIO API `9000`，
全部仅绑定 `127.0.0.1`，由同宿主的外部网关反代并终止 TLS；PostgreSQL 与 Redis 不发布宿主端口。容器内部始终通过服务名
和标准端口互联。生产加固细节见[运维手册](docs/agent-platform/09-运维手册/README.md)。

## 关联项目

- `aether-dashboard`：用户与管理控制台。
- `aether-deep-agent-service`：复杂任务规划、执行与流式回调。
- `aether-mcp-server`：受 Java 委派 JWT 约束的 MCP 工具服务。

## 文档

完整分类入口见 [docs/README.md](docs/README.md)。

| 文档                                                              | 内容                                  |
|-----------------------------------------------------------------|-------------------------------------|
| [业务说明](docs/agent-platform/00-项目文档/02-业务说明/README.md)           | 产品定位、业务对象、流程、治理与典型场景                |
| [项目总体说明](docs/agent-platform/00-项目文档/01-项目总体说明/README.md)       | 平台整体架构、部署与排障                        |
| [数据库设计](docs/agent-platform/00-项目文档/04-数据库设计/README.md)         | 全部表结构、索引与状态字典（V1-V32）               |
| [API 参考](docs/agent-platform/00-项目文档/05-API参考/README.md)        | 全部 REST 端点、权限路径与 SSE 事件             |
| [架构设计](docs/agent-platform/00-项目文档/03-架构设计/README.md)           | 模块分层、认证、HMAC、RAG、Deep Agent 与工作流运行时 |
| [工作流业务集成](docs/agent-platform/00-项目文档/06-工作流业务集成/README.md)     | 服务账号、Webhook、幂等启动和业务回调验签            |
| [Agent 平台文档](docs/agent-platform/)                              | 前端对接与平台演进（01-09）                    |
| [历史对话性能优化方案](docs/agent-platform/00-项目文档/07-历史对话性能优化/README.md) | 对话上下文缓存、摘要和性能优化                     |
