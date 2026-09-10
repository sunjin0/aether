# 最小离线试点发布

该发布形态只启用离线 Sandbox 能力：通用文档、经审批的本地 Python/Node 分析及固定命令代码检查。网页采集与任何需要下载依赖的模板必须保持禁用。

发布使用仓库根目录的 `docker-compose.prod.yml`，其中 `sandbox-runner` 与两个沙箱运行时镜像均已包含在默认服务集内，无需额外 profile。

## 前提

- 一台专用 Linux VM，Docker Engine 与 Docker Compose v2；不要与数据库、业务宿主机或 CI Runner 共用。
- 生产级随机密码与全部共享密钥；不要使用模板中的 `replace-with-*` 值。
- 四个源码仓库私有，须确认部署机的 git 能直接拉取（`git ls-remote <仓库地址>` 通过）；凭据由宿主机 git 配置提供，无需额外令牌。
- 沙箱运行时镜像由 `sandbox-runtime-python` / `sandbox-runtime-node` 两个一次性服务构建，Runner 会在其 `service_completed_successfully` 后才启动。

## 构建与启动

```sh
cp .env.prod.example .env.prod
# 编辑 .env.prod，替换所有 replace-with-* 值
./scripts/fetch-sources.sh   # 用部署机 git 凭据检出源码；记录输出的提交 SHA
docker compose --env-file .env.prod -f docker-compose.prod.yml config
docker compose --env-file .env.prod -f docker-compose.prod.yml build
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
```

源码由 `scripts/fetch-sources.sh` 用部署机的 git 凭据检出到 `AETHER_SOURCE_ROOT`（默认 `.sources/`），Compose 再从本地目录构建。试点环境应把各 `AETHER_*_GIT_REF` 固定到已验证的 tag 或提交 SHA，而不是 `master`。Flyway 在 Admin 启动时应用 Sandbox 迁移。

## 上线检查

1. Dashboard 能打开 `/agent/sandbox`，模板和审计页可用。
2. `generic-document` 可创建、审批/取消并留下事件。
3. Runner 只领取 `network=NONE` 的任务；网页采集模板保持停用。
4. 在 Admin 容器中确认 `AETHER_SANDBOX_RUNNER_TOKEN` 与 Runner 一致。
5. 验证 Runner 主机没有业务源码、数据库卷或用户主目录被挂入任务容器。
6. `AETHER_SANDBOX_ALLOWED_IMAGE_DIGESTS` 已填入固定运行时镜像的 sha256 摘要，而非留空。

不要把 Docker Socket 共享给任何其他服务——`sandbox-runner` 是 `docker-compose.prod.yml` 中唯一挂载该 Socket 的服务。该试点不能替代第五、六期的 egress 代理、Kubernetes/gVisor/Firecracker 与镜像供应链治理。
