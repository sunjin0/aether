# 最小离线试点发布

该发布形态只启用离线 Sandbox 能力：通用文档、经审批的本地 Python/Node 分析及固定命令代码检查。网页采集与任何需要下载依赖的模板必须保持禁用。

## 前提

- 一台专用 Linux VM，Docker Engine 与 Docker Compose v2；不要与数据库、业务宿主机或 CI Runner 共用。
- 三个仓库已检出；在 `.env.sandbox-pilot` 中填写实际的绝对 `AETHER_*_SOURCE` 路径。
- 生产级随机密码与三个既有共享密钥；不要使用示例值。

## 构建与启动

```sh
cp .env.sandbox-pilot.example .env.sandbox-pilot
# 编辑 .env.sandbox-pilot，替换所有 replace-with-* 值
docker compose --env-file .env.sandbox-pilot -f docker-compose.sandbox-pilot.yml config
docker compose --env-file .env.sandbox-pilot -f docker-compose.sandbox-pilot.yml build
docker compose --env-file .env.sandbox-pilot -f docker-compose.sandbox-pilot.yml up -d
docker compose --env-file .env.sandbox-pilot -f docker-compose.sandbox-pilot.yml ps
```

首个命令会构建当前工作区源码，而不是从远程 `master` 拉取旧代码。Flyway 在 Admin 启动时应用 Sandbox 迁移。

## 上线检查

1. Dashboard 能打开 `/agent/sandbox`，模板和审计页可用。
2. `generic-document` 可创建、审批/取消并留下事件。
3. Runner 只领取 `network=NONE` 的任务；网页采集模板保持停用。
4. 在 Admin 容器中确认 `AETHER_SANDBOX_RUNNER_TOKEN` 与 Runner 一致。
5. 验证 Runner 主机没有业务源码、数据库卷或用户主目录被挂入任务容器。

不要把 Docker Socket 共享给任何其他服务。该试点不能替代第五、六期的 egress 代理、Kubernetes/gVisor/Firecracker 与镜像供应链治理。
