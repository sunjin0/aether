# Aether Admin

Aether 的 Java 后端与平台聚合部署项目。提供用户与权限、Agent 配置、普通 Agent 聊天、Deep Agent 运行编排、MCP 工具审批、知识库检索和文件存储能力。

## 模块

| 模块 | 说明 |
| --- | --- |
| `common` | 通用响应、认证、权限、Redis、异常和国际化。 |
| `api` | 实体、VO、Mapper、服务接口、数据库迁移与国际化资源。 |
| `storage` | MinIO 等对象存储适配。 |
| `biz` | Agent、知识库、用户与业务实现。 |
| `admin` | Spring Boot 管理/API 应用，默认容器端口 `8080`。 |
| `front` | 前端应用壳模块。 |

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

生产环境主要变量见 `.env.example`：`DB_URL`、`REDIS_HOST`、`MINIO_*`、`AETHER_DEEP_AGENT_*` 与 `AETHER_MCP_DELEGATION_SECRET`。普通聊天默认关闭查询重写，避免在主模型调用前增加一次同步模型请求。

## 一键 Docker 部署

`docker-compose.all.yml` 会部署 PostgreSQL、Redis、MinIO、Admin、Dashboard、Deep Agent 与 MCP。业务服务源码由 BuildKit 从 Git 仓库拉取；私有仓库需提供只读 GitHub Token。

```powershell
Copy-Item .env.all.example .env.all
# 编辑 .env.all，至少设置 GIT_AUTH_TOKEN 及生产环境密钥
docker compose --env-file .env.all -f docker-compose.all.yml -p aether up -d --build
```

默认宿主机端口均为非默认值：PostgreSQL `15432`、Redis `16379`、MinIO `19000/19001`、Admin `18080`、Dashboard `18001`、Deep Agent `18010`、MCP `18000`。容器内部仍通过服务名和标准端口互联。

## 关联项目

- `aether-dashboard`：用户与管理控制台。
- `aether-deep-agent-service`：复杂任务规划、执行与流式回调。
- `aether-mcp-server`：受 Java 委派 JWT 约束的 MCP 工具服务。
