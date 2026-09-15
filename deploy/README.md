# Tag 发布

本仓仅在推送 `v*` 标签后发布自己的服务：admin、front。

## 服务器目录

```text
/opt/aether-server/
  .env
  aether/releases/<UTC时间>-<tag>/
  dashboard/releases/<UTC时间>-<tag>/
  deep-agent/releases/<UTC时间>-<tag>/
  mcp/releases/<UTC时间>-<tag>/
```

项目目录由工作流自动创建；Aether 也是一个项目目录，不占用部署根目录。每次标签发布都会创建如 `20260912T103000Z-v0.1.2` 的独立版本目录。

## 配置

本仓的 `deploy/.env.example` 是服务器统一配置模板。

首次部署前，在服务器创建统一配置：

```bash
mkdir -p /opt/aether-server
cp deploy/.env.example /opt/aether-server/.env   # 仅在 Aether 仓执行
chmod 600 /opt/aether-server/.env
```

四个仓库均配置 GitHub Secret `DEPLOY_SSH_KEY`，以及 Variables：`DEPLOY_HOST`、`DEPLOY_USER`、`DEPLOY_PORT`（默认 22）和 `DEPLOY_ROOT=/opt/aether-server`。

工作流不会上传或覆盖 `.env`。各项目的 Compose 均读取同一个 `/opt/aether-server/.env`，因此共享数据库、Redis、网络与跨服务密钥只需维护一次。

## 发布与回滚

工作流先完成测试和构建，再上传本项目的发布产物，并以 `--no-deps` 只更新本项目服务。不会执行 `down` 或 `--remove-orphans`。

成功记录保存在 `<项目>/.releases/aether.current`；回滚时：

```bash
cd /opt/aether-server/aether
source .releases/aether.previous
bash "releases/$release_id/admin/deploy/release.sh" aether "$tag" "$PWD" "$release_id"
```

数据库迁移为前向迁移，镜像回滚不会回退 schema。

## 本地 Docker 调试

本地调试不连接服务器。它会打包 Admin 和 Front，构建本地镜像，并启动 PostgreSQL、Redis、Admin 和 Front；PostgreSQL 复用已有的 `pgvector_data` 数据卷，Redis、MinIO、容器和网络使用 `aether-local-*` 名称。

在 Linux、macOS、WSL 或 Git Bash 中执行：

```bash
cp deploy/.env.example deploy/dev/.env.local
# 编辑 deploy/dev/.env.local，填入本地调试所需的密钥与对象存储配置
bash deploy/dev/local-debug.sh
```

默认访问地址为 Admin `http://127.0.0.1:18080`、Front `http://127.0.0.1:18081`。可通过 `LOCAL_POSTGRES_VOLUME`、`LOCAL_ADMIN_PORT`、`LOCAL_FRONT_PORT`、`LOCAL_POSTGRES_PORT`、`LOCAL_REDIS_PORT` 或 `AETHER_LOCAL_RELEASE_TAG` 覆盖数据卷、端口和本地镜像标签。

### 与卫星仓共用一套调试分组

Dashboard、MCP、Deep Agent 三个仓各自维护 `deploy/dev/compose.yml` 覆盖层，项目名统一为 `aether-local-debug`，因此全部容器在 Docker Desktop 里归入同一分组。网络 `aether-local-debug-services` 由本仓的调试栈创建，卫星仓只加入不创建，所以**必须先起本仓再起卫星仓**。

各仓自己构建、自己启动：

```bash
cd <aether-dashboard>
bash deploy/dev/local-build.sh && bash deploy/dev/local-up.sh
```

本仓的 `deploy/dev/local-debug.sh` 也会按需代拉起卫星仓：在 `deploy/dev/.env.local` 里配置目录即可，留空或目录不存在则跳过（此时行为与只跑后端完全一致）。

```bash
AETHER_LOCAL_DASHBOARD_DIR=C:/path/to/aether-dashboard
AETHER_LOCAL_MCP_DIR=C:/path/to/aether-mcp-server
AETHER_LOCAL_DEEP_AGENT_DIR=C:/path/to/aether-deep-agent-service
```

脚本只负责启动、不负责构建，卫星仓镜像仍需各自的 `local-build.sh` 先构建好；两边镜像标签也相互独立，各仓默认都是 `dev-local`。

| 服务 | 地址 |
| --- | --- |
| Admin | `http://127.0.0.1:18080` |
| Front | `http://127.0.0.1:18081` |
| Dashboard | `http://127.0.0.1:18082` |
| MCP | `http://127.0.0.1:18000` |
| Deep Agent | `http://127.0.0.1:18010` |
| PostgreSQL | `127.0.0.1:15432` |
| Redis | `127.0.0.1:16379` |

### 同一项目名下的注意事项

分组是按 `com.docker.compose.project` 归类的，所以四个仓的 Compose 文件共享 `aether-local-debug` 这一个项目名。由此带来两点必须知道的行为：

- 每个仓只声明自己的服务，于是 Compose 会把**别的仓的容器当成孤儿**并打印 `Found orphan containers (...)` 警告。这是正常现象，可以忽略。**绝对不要加 `--remove-orphans`**——它会把其他仓的容器一并删掉。
- `docker compose -p aether-local-debug down` 在哪个仓里执行，就只会移除该仓的容器，不会一次清空整个分组。要停掉全部，需要四个仓各执行一次。

本仓的 `local-debug.sh` 与三个卫星仓的 `local-up.sh` 都不带 `--remove-orphans`。
