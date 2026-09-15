# 最小离线试点发布

该发布形态只启用离线 Sandbox 能力：通用文档、经审批的本地 Python/Node 分析及固定命令代码检查。网页采集与任何需要下载依赖的模板必须保持禁用。

“离线”不是靠配置约定，而是靠运行时强制：`sandbox-runner` 起任务容器时固定带 `--network none`，并同时施加 `--read-only`、`--cap-drop ALL`、`--security-opt no-new-privileges`、`--pids-limit`、`--memory` / `--cpus` 与只读输入挂载（`sandbox-runner/runner.py:199`、`runner.py:266`）。任务容器因此既没有出网路径，也没有可写根文件系统。

## 组件落点

试点不引入新的发布单元，全部沿用各仓现有的按仓发布模型：

| 组件 | 定义位置 | 说明 |
| --- | --- | --- |
| `sandbox-runner` | aether-mcp-server 仓 `deploy/compose.yml` | 常驻容器，**全栈中唯一挂载 `/var/run/docker.sock` 的服务** |
| `sandbox-runtime-python` / `sandbox-runtime-node` | 同上 | `profiles: [build]` 的一次性构建服务：只产出运行时镜像，不作为常驻容器启动 |
| 任务领取与容器编排 | aether-mcp-server 仓 `sandbox-runner/runner.py` | 向 Admin 领任务、按模板起容器、回传结果 |
| 模板、审批与审计 | Aether 仓后端 | 迁移 `V49__sandbox_task_platform.sql` 起落地，Admin 启动时由 Flyway 应用 |
| 操作入口 | aether-dashboard 仓 | `/agent/sandbox` 页面（模板与审计） |

`sandbox-runner` 与两个运行时镜像都归属 **aether-mcp-server** 仓的 `mcp` 发布单元：`deploy/release.sh` 里 `services` 含这四个服务，而 `running` 只含 `mcp` 与 `sandbox-runner`——两个运行时镜像是 `profiles: [build]` 的构建专用服务，永远不会被 `up`。

## 前提

- 一台专用 Linux VM，Docker Engine 与 Docker Compose v2；不要与数据库、业务宿主机或 CI Runner 共用。
- 生产级随机密码与全部共享密钥；不要使用模板中的 `replace-with-*` 值。
- 部署机不需要 git 凭据，也不需要源码：镜像在 CI 中构建后随发布产物上传，部署机只 `docker load`，发布脚本以 `--no-build` 起容器。
- 部署机不参与构建。设置 `AETHER_PREBUILT_IMAGES=true` 后，`deploy/release.sh` 跳过 `--profile build build`，改为逐个 `docker image inspect` 校验镜像是否已就位（`deploy/release.sh:34`）。
- **注意该校验只覆盖 `running` 里的服务**（`mcp` 与 `sandbox-runner`），两个 `profiles: [build]` 的运行时镜像不在其中。也就是说 `AETHER_PREBUILT_IMAGES=true` 下运行时镜像缺失**不会**在发布时暴露，而是要等第一个任务执行才报错。发布后需按“上线检查”第 7 条自行确认这两个镜像已在部署机上 `docker load`。

## 配置

统一配置只保存为 `/opt/aether-server/.env`，由四个仓的发布脚本共同读取，权限 600。不要在任一项目目录或 release 目录下另建环境文件。

Sandbox 相关键：

| 键 | 作用 |
| --- | --- |
| `AETHER_SANDBOX_RUNNER_TOKEN` | 必填，compose 以 `:?` 强校验。Admin 与 Runner 两侧必须一致，Runner 用 `X-Aether-Runner-Token` 头调用 Admin |
| `AETHER_SANDBOX_RUNNER_ID` | Runner 标识，默认 `sandbox-prod-1`；多 Runner 时用于区分 |
| `AETHER_SANDBOX_PYTHON_IMAGE` / `AETHER_SANDBOX_NODE_IMAGE` | 默认 `aether-sandbox-{python,node}:release-${AETHER_RELEASE_TAG}`，随发布标签变动，通常无需手改 |
| `AETHER_SANDBOX_ALLOWED_IMAGE_DIGESTS` | 逗号分隔的 sha256 白名单。**留空即全部任务被拒**：`runner.py:144` 做的是白名单成员判定，空集合下任何模板都不通过，是 fail-closed 而非“不限制” |

因此修改模板或轮换运行时镜像后，必须同步更新 `AETHER_SANDBOX_ALLOWED_IMAGE_DIGESTS`，否则任务会全部失败。

## 发布

各仓对自己的 `v*` 标签负责，标签只部署本仓的服务：

```sh
# 在 aether-mcp-server 仓（含 sandbox-runner 与两个运行时镜像）
git tag v1.2.3 && git push origin v1.2.3
```

工作流先测试构建、再上传发布产物，最后由部署机上的发布脚本执行。发布脚本：

- 用 `flock` 串行化（默认锁文件 `~/.aether-deploy.lock`），避免多仓并发发布互相打断；
- 导出 `COMPOSE_IGNORE_ORPHANS=true`，并以 `up -d --no-deps --no-build --wait --wait-timeout 300` 只更新本项目服务；
- **不执行** `docker compose down` 或 `--remove-orphans`，也不重建已有的 PostgreSQL / Redis；
- 发布目录为 `/opt/aether-server/<项目>/releases/<UTC时间>-<tag>`。

## 回滚

每次发布前把 `.releases/mcp.current` 拷成 `.releases/mcp.previous`，再写入本版的 `release_id` 与 `tag`。这两个文件只记录版本号，回滚要靠**当时那一版归档下来的 release 目录**：

```sh
cd /opt/aether-server/mcp
source .releases/mcp.previous   # 取回上一版的 release_id 与 tag
bash "releases/$release_id/mcp/deploy/release.sh" mcp "$tag" "$PWD" "$release_id"
```

末尾这行由发布脚本自己打印（`deploy/release.sh:65`），照抄即可。要点：

- 回滚跑的是**旧 release 目录里的那份 `release.sh`**，不是当前仓里的脚本，因此不要删除历史 release 目录。
- `.previous` 只在第二次发布之后才存在；首次发布若出问题，没有可回滚的上一版。
- 不要手工 `docker compose up` 指定旧镜像：那样会绕过 `.releases` 的记录，使下一次回滚指向错误版本。

## 上线检查

1. Dashboard 能打开 `/agent/sandbox`，模板和审计页可用。
2. `generic-document` 可创建、审批/取消并留下事件。
3. 抽查任一已执行任务的容器参数，确认带 `--network none` 与 `--read-only`；网页采集模板保持停用。
4. 确认 `AETHER_SANDBOX_RUNNER_TOKEN` 在 Admin 与 Runner 两侧取值一致（两侧都从 `/opt/aether-server/.env` 注入）。
5. 验证 Runner 主机没有业务源码、数据库卷或用户主目录被挂入任务容器——任务容器只应看到 `/work/{resources,input,output}` 三个卷。
6. `AETHER_SANDBOX_ALLOWED_IMAGE_DIGESTS` 已填入固定运行时镜像的 sha256 摘要，而非留空（留空会导致任务全部被拒）。
7. 两个运行时镜像确实已加载：`docker image ls 'aether-sandbox-*'` 应能看到 `aether-sandbox-python:release-<tag>` 与 `aether-sandbox-node:release-<tag>`，且其 sha256 与第 6 条的白名单一致。发布脚本不校验它们（见“前提”），漏载只会在跑第一个任务时暴露。
8. `docker ps` 中不存在 `sandbox-runtime-*` 常驻容器——它们只在构建阶段出现，若长期存在说明发布流程被改动过。

## 边界

该试点不能替代第五、六期的 egress 代理、Kubernetes/gVisor/Firecracker 隔离与镜像供应链治理：`--network none` 只保证任务容器不出网，不限制 Runner 自身；Runner 持有 Docker Socket，因此其宿主机必须专机专用，且不要把该 Socket 共享给任何其他服务。
