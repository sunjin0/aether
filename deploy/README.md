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
