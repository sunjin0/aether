# Tag 发布

唯一入口为 .github/workflows/release.yml：本仓推送 v* tag → 测试 → 上传 → 部署。各项目版本独立。
本项目负责：admin、front。

## 配置

- 服务器安装 Docker Compose（支持 --wait）、Bash、rsync、Python 3、flock；部署用户可以操作 Docker。
- GitHub Secret：DEPLOY_SSH_KEY；Variables：DEPLOY_HOST、DEPLOY_USER、DEPLOY_PORT（默认 22）、DEPLOY_PATH。
- 建议每仓使用独立部署目录，例如 /opt/aether；路径必须为无空格绝对路径。
- 首次发布前将本目录 .env.example 复制到服务器 $DEPLOY_PATH/deploy/.env 并填写。工作流不会上传或覆盖真实配置。
- 同机各项目使用同一部署用户、AETHER_SHARED_NETWORK；跨服务共享密钥和数据库凭据保持一致。

## 流程

compose.yml 是本项目完整配置，无需其他仓库的 Compose。Dockerfile、环境模板和脚本都在 deploy/；GitHub 入口因平台约定保留在 .github/workflows/。
Actions 上传至 release/<tag>/<component>/，仅构建本仓镜像并以 --no-deps 更新本仓服务。
保留 aether-prod 项目标识、容器名、网络和数据卷名，避免迁移时丢失资源关联。禁止执行 down 或 --remove-orphans。
用户目录的 .aether-prod-deploy.lock 串行化同用户部署。

首次 tag 自动创建 PostgreSQL/Redis 和共享网络；后续使用 --no-recreate 保留基础设施。deploy/.env 中可用 COMPOSE_PROFILES=minio 启用 MinIO，并同步存储配置。PG16 升级必须先做数据迁移，改镜像不能代替迁移。Admin 健康后才更新 Front。

健康检查失败即发布失败；Runner 没有专用探针时只能检查进程状态。成功版本记录在 .releases/aether.tag，上一成功版本为 .previous.tag；失败可能已更新部分本项目容器。

## 回滚

保留旧 tag 产物和镜像，调用同一入口：

    cd "$DEPLOY_PATH"
    tag="$(cat .releases/aether.previous.tag)"
    bash "release/$tag/admin/deploy/release.sh" aether "$tag" "$DEPLOY_PATH"

数据库 schema 不随镜像回滚。旧流程 tag 不具备新目录契约，首次切换前应记录现有镜像，必要时由运维定向恢复。
旧 .env.prod 中本项目所需变量迁到 deploy/.env；服务器不再需要根目录 Compose 或 git 检出目录。
