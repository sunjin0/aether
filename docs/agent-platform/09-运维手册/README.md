# Agent 平台 — 运维手册

> 更新日期：2026-09-12

---

## 数据库与 Flyway 迁移

建表与数据迁移全部由 Flyway 管理，迁移脚本位于 `api/src/main/resources/db/migration/postgresql/`
（共 **209 个文件，V1 ~ V212**，版本号有 3 处空缺——退役迁移整段删除后不会回填）：

- `V1__init.sql`：完整初始化（建表 + 种子数据 + 公共索引 + pgvector）。
- `V2` 之后为增量变更（会话摘要字段、Deep Agent、混合检索、检索评测、服务账号、工作流运行时、
  触发器、菜单/权限种子、评测可靠性增强，以及 `V178` 主动下线租户与可观测性表等）。

Admin/Front 启动时自动执行迁移；相关配置见 `admin/src/main/resources/application.yml` 的 `spring.flyway`
块（locations、baseline-on-migrate、validate-on-migrate）。

### 本地初始化（PostgreSQL + pgvector）

> 仓库**不再提供**本地基础设施编排：`docker-compose.postgresql.yml` 与 `deploy/docker-compose.infrastructure.yml`
> 均已下线（后者见 `d134156 chore(deploy): 下线被生产编排取代的旧 Compose 与失效门禁脚本`）。
> 本机需自备 PostgreSQL（pgvector 镜像）与 Redis，按 `api/src/main/resources/application-dev.yml`
> 的默认值监听 `localhost:5432/aether`（`sunjin` / `192837`）与 `127.0.0.1:6379`，或改用 `DB_URL` /
> `DB_USERNAME` / `DB_PASSWORD` 环境变量指向已有实例。

从零重建这套实例的最小形态：

```sh
docker run -d --name aether-postgres --restart always \
  -e POSTGRES_DB=aether -e POSTGRES_USER=sunjin -e POSTGRES_PASSWORD=192837 \
  -p 5432:5432 -v pgvector_data:/var/lib/postgresql \
  pgvector/pgvector:pg18

docker run -d --name aether-redis --restart always -p 6379:6379 \
  -v aether_redis-data:/data redis:7-alpine
```

镜像串与 `.github/workflows/release.yml`、`docker-compose.prod.yml`、`.env.prod.example` 四处
**必须完全一致**：三个 `@SpringBootTest` 上下文测试会对 CI 的空库执行全部迁移，镜像不同就等于
「迁移在 A 上验证、在 B 上执行」，正是要消除的那类分歧。

四个容易踩的坑：

- **必须要挂到 `/var/lib/postgresql`，不能挂 `/var/lib/postgresql/data`。** PG18 起 `PGDATA`
  改为 `<挂载点>/18/docker`，官方据此要求挂上层目录。若仍挂 `.../data`，数据会落到该路径下的
  容器层（不在 `pgvector_data` 卷里），`docker rm` 一次就丢库。挂对了之后实际数据在
  `pgvector_data/18/docker`。
- **必须是 `--restart always`，不能是 `unless-stopped`。** 本机这套容器原先用的是
  `unless-stopped`：Docker Desktop 重启时（本机 2026-09-11 就发生过一次，进程启动时间与容器
  `FinishedAt` 只差 4 秒）它会发送 SIGTERM，Postgres 干净退出（ExitCode 0），而
  `unless-stopped` 对「daemon 停止前已处于停止态」的容器不再拉起——于是容器静静躺平，
  下一次 `mvn test` 以 `Connection to localhost:5432 refused` 收场，看起来像代码挂了。
  `always` 不受停止态影响，daemon 一起来就拉。
- **`POSTGRES_USER` / `POSTGRES_PASSWORD` 只在数据目录为空时生效。** 卷里已有集群时它们被忽略，
  实际可用角色以当初初始化时为准，与 `docker inspect` 出来的 env 无关。（这两个容器的 env 曾被
  设成 `aether` / `aether_dev` 而实际角色是 `sunjin` / `192837`，2026-09-12 重建时已让 env
  与实际一致，但这条规则本身不变。）排查连不上时以
  `docker exec aether-postgres psql -U sunjin -d aether -tAc 'select current_user'` 为准。
- **本机容器的镜像是 `0.8.2-pg18-trixie`，不是上面的 `pg18`。** 区别在基底发行版：前者是
  Debian trixie（glibc 2.41），后者是 bookworm（glibc 2.36）。本机这套数据目录是用 trixie
  那版初始化的，换成 `pg18` 后每次连库都会告警
  `database "aether" has a collation version mismatch`（2.41 → 2.36），文本索引的排序需要
  `REINDEX DATABASE aether` 重建后才可信。因此**已有数据的本机实例保持 trixie 镜像**；
  按上面 snippet 从零建（空卷）则用 `pg18` 没有这个问题。两者同为 PG18，
  仅仅补丁版本与 pgvector 小版本不同（18.4/0.8.2 对 18.6/0.8.6），不影响迁移可用性。

> **容器名与 compose 项目**：本机这三个容器已于 2026-09-12 重建，不再带
> `com.docker.compose.project=aether-infrastructure` 标签，`docker compose ls` 里不会再出现
> 那个幽灵项目（`deploy/docker-compose.infrastructure.yml` 早在 `d134156` 删除）。
> `aether-minio` 容器一并移除了（prod 用阿里云 OSS，本地栈不需要它）。卷 `aether_minio-data`
> **保留未删**（无人引用，属孤儿卷）：里面可能有验收栈留下的对象数据，删不删都不影响本机开发，
> 确认无用后再自行 `docker volume rm aether_minio-data`。
>
> 卷归属容易混：`docker-compose.acceptance.yml` 没有写 `name:`，项目名取目录名 `aether`，
> 所以它的卷是 `aether_postgres-data` / `aether_redis-data` / `aether_minio-data`。
> 其中：
>
> - `aether_postgres-data` **是验收栈专属，别当开发库用**——里面是 PG16 集群（卷根有
>   `PG_VERSION=16`）。验收栈已升 PG18，下次 `up` 前必须先删掉该卷重新初始化，否则 postgres
>   容器会因「检测到旧数据目录」直接退出。开发库在 `pgvector_data`，不受影响。
> - `aether_redis-data` 由**开发容器与验收栈共用**（两处都挂它）。Redis 只存会话、权限、
>   限流与分布式锁，compose 本身也写明「丢失时允许冷启动重建」，共用无害；
>   要彻底隔离就给开发容器另起一个卷名。

```powershell
# 实例就绪后：
docker exec aether-postgres pg_isready -U sunjin -d aether
mvn -pl admin -am -DskipTests install
mvn -pl admin org.springframework.boot:spring-boot-maven-plugin:2.7.18:run -Dspring-boot.run.profiles=dev
```

> **Springfox 兼容性已由主源码统一处理。** Springfox 2.10.5 与 Spring Boot 2.7 的 handler mapping
> 不兼容：Spring MVC 5.3 会注册基于 `PathPatternRequestCondition` 的映射，而 Springfox 只认
> `PatternsRequestCondition`，`documentationPluginsBootstrapper` 启动时对前者调用 `getPatterns()`
> 抛 NPE，上下文取消、进程退出。`common/src/main/java/com/aether/config/SpringfoxCompatibilityConfig.java`
> 在 `knife4j.enable=true` 时注册一个 BeanPostProcessor，把带 pattern parser 的 handler mapping
> 从 Springfox 的视图里剔除（只影响接口文档的路径匹配，不影响业务路由）。条件不成立时该配置根本不注册，
> 因此 prod profile 的 bean 集合与改动前完全一致。`spring.mvc.pathmatch.matching-strategy=ant_path_matcher`
> 治不了它（已实测：用命令行参数强制指定同样崩溃）。测试侧不再需要任何补丁。

### 向量基础结构

- `knowledge_document_chunk.embedding`：固定 `vector(1536)`，对应 `text-embedding-3-small`。
- 索引：HNSW 余弦（`vector_cosine_ops`）+ 词法 GIN（`to_tsvector('simple', content)`）。
- 扩展：`CREATE EXTENSION vector`（V1 内自动执行）。

### 生产切换/回滚

- 维护窗口内对旧库做完整备份，通过 pgloader 导入数据到空库后再启动应用（Flyway baseline）。
- 保留旧库备份至少 14 天；切换失败时恢复旧配置指向旧库。
- `FLYWAY_ENABLED=false` 可关闭自动迁移（仅限完全受控的部署）。

#### PG 16 → 18 迁移（不是改一行镜像就能完成）

`POSTGRES_IMAGE` 从 `pgvector/pgvector:pg16` 改为 `pgvector/pgvector:pg18`、挂载点从
`/var/lib/postgresql/data` 改为 `/var/lib/postgresql`（PG18 起 `PGDATA` 变成
`<挂载点>/18/docker`，官方要求挂上层目录）。**PG18 无法就地读取 PG16 的数据目录，
`pg_upgrade` 也不适用**（版本不同、且容器里没有旧二进制），只能逻辑备份 + 恢复。

**失败形态是响亮的，不会静默读错数据**：`postgres-data` 卷里是 PG16 集群，其数据就在卷根，
而 pg18 的 entrypoint 会扫描 `/var/lib/postgresql`、`/var/lib/postgresql/data`、
`/var/lib/postgresql/*/docker` 找 `PG_VERSION`，命中后调用 `docker_error_old_databases`
并 `exit 1`。表现为容器反复重启（`docker logs aether-postgres` 会明确打印检测到旧数据目录），
应用侧则是连不上库。所以**别指望「改完起不来」是配置写错了**，它就是迁移没做。

维护窗口内的步骤：

```sh
cd "$DEPLOY_PATH"
mkdir -p backup
set -a; . ./.env.prod; set +a
COMPOSE="docker compose --env-file .env.prod -f docker-compose.prod.yml"

# 1) 先停掉旧服务，保证数据目录不会被两个进程同时打开
$COMPOSE stop postgres

# 2) 用【旧】镜像 + 同一个卷起临时容器做逻辑备份。镜像的 entrypoint 会自己把服务拉起来，
#    所以 pg_dumpall 要在容器内跑、不能当一次性命令用。
#    卷名带项目前缀：aether-prod_postgres-data；不发布端口，避免与任何东西冲突。
docker run -d --name pg16-dump \
  -v aether-prod_postgres-data:/var/lib/postgresql/data \
  pgvector/pgvector:pg16
for i in $(seq 1 30); do docker exec pg16-dump pg_isready -U "$POSTGRES_USER" >/dev/null 2>&1 && break; sleep 2; done
docker exec pg16-dump pg_dumpall -U "$POSTGRES_USER" -d "$POSTGRES_DB" > "backup/aether-$(date +%F).sql"
docker rm -f pg16-dump
# 必须校验：非空、含 CREATE TABLE、以 PostgreSQL database dump complete 结尾
wc -c "backup/aether-"*.sql && tail -3 "backup/aether-"*.sql

# 3) 把旧卷整卷拷一份留档（比删除更稳，随时可退回）；不要 rm
docker volume create aether-prod-postgres-data-pg16
docker run --rm -v aether-prod_postgres-data:/from -v aether-prod-postgres-data-pg16:/to \
  alpine cp -a /from/. /to/

# 4) 清空原卷（保留卷名，compose 与卷映射都不用改）
$COMPOSE down
docker run --rm -v aether-prod_postgres-data:/v alpine sh -c 'rm -rf /v/* /v/.[!.]*'

# 5) 确认 .env.prod 的 POSTGRES_IMAGE 已是 pg18（挂载点已在 compose 里改好），起空库
grep '^POSTGRES_IMAGE=' .env.prod
$COMPOSE up -d postgres
$COMPOSE ps postgres                                            # healthy

# 6) 恢复数据（POSTGRES_USER/PASSWORD 此时才真正生效——空卷初始化）
docker exec -i aether-postgres psql -U "$POSTGRES_USER" -d postgres < "backup/aether-"*.sql

# 7) 起应用；admin 会跑 Flyway（此时应为「无待应用迁移」，因为 flyway_schema_history 已随备份恢复）
$COMPOSE up -d --wait
docker exec aether-postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -tAc "select count(*) from flyway_schema_history"             # 应为 209
docker exec aether-postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -tAc "select extversion from pg_extension where extname='vector'"
```

> `pg_dumpall -U "$POSTGRES_USER"` 走容器内 unix socket，官方镜像的本地连接默认为 `trust`，
> 不需要密码；`set -a` 载入 `.env.prod` 是为了拿到正确的角色名与库名。

回退：把 `aether-prod-postgres-data-pg16` 拷回 `aether-prod_postgres-data`、
`POSTGRES_IMAGE` 改回 `pg16`、挂载点改回 `/var/lib/postgresql/data` 后 `up -d`。
**备份转储文件至少留存 14 天**，它与旧卷是两道独立保险。

> 恢复完成后核对一次向量扩展：`select extversion from pg_extension where extname='vector'`。
> 数据来自 PG16 时该值以镜像默认版本为准（本仓库固定的 `pg18` 镜像为 0.8.6），
> 只要 ≥ 备份中记录的值即可；pgvector 只支持前向升级，若需升级用
> `ALTER EXTENSION vector UPDATE`。

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

仓库共四个 Compose 文件：`docker-compose.yml`（仅 Admin/Front，接入外部基础设施）、`docker-compose.prod.yml`
（生产全栈，见下）、`docker-compose.release.yml`（发布叠加文件，见「发布与回滚」）、
`docker-compose.acceptance.yml`（发布前验收，从本地工作区构建）。

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

常态走**打 tag 自动发布**；`fetch-sources.sh` 的源码构建保留为离线灾备回退。

#### 打 tag 自动发布（常态路径）

四个仓库各有一份 `.github/workflows/release.yml`，触发条件都是推送 `v*` tag。**四个仓必须打同一个版本号**（如都打 `v0.1.0`）。

```
打 tag v0.1.0
  ├─ aether         测试 → mvn package → rsync admin.jar / front.jar ─┐
  ├─ dashboard      tsc + jest → npm run build → rsync dist/          │→ 部署机暂存区
  ├─ deep-agent     pytest → rsync 构建输入                            │   $DEPLOY_PATH/release/<tag>/
  └─ mcp            pytest → rsync 构建输入 ──────────────────────────┘
                    随后 aether 的 workflow 继续：compose build && up -d --wait
```

- **只有 aether 的 tag 会部署**（整套 compose 在它那里）。其余三仓只上传产物；三仓全绿后再给 aether 打 tag，否则 aether 的预检会点名缺少哪个组件目录并中止，此时线上容器尚未被动。
- 不走镜像仓库：Actions 只构建**产物**并经 SSH 传输（约 280 MB/次），镜像在部署机上构建，架构天然匹配宿主。
- 发布记录写在 `release/<tag>/admin/release-manifest.txt`（tag、commit、构建时间、本次携带的迁移版本区间），部署日志会打印。
- **测试门禁无例外**：aether 的 525 个用例与 dashboard 的 32 套件 / 98 用例全部是阻断式门禁，没有排除清单。aether 的三个 `@SpringBootTest` 上下文测试（`AdminApplicationTests` / `SmsControllerTest` / `FrontApplicationTests`）由 workflow 的 `services:` 提供 pgvector/pg18 与 redis:7 满足依赖；它们会对空库执行全部迁移，等于每次发布都顺带验证「迁移能否从零应用」。三者均不携带任何 Springfox 补丁，dev profile 的上下文靠主源码的 `SpringfoxCompatibilityConfig` 才能起来——所以这道门禁同时守着「应用能在 dev profile 下启动」。
- **回滚 = 改 `.env.release` 里的 tag 再 `up -d`**，秒级、无需重传（该 tag 的镜像仍在部署机上，tag 形如 `release-<版本>`，不覆盖）：

  ```sh
  cd "$DEPLOY_PATH"
  printf 'AETHER_RELEASE_TAG=%s\n' v0.0.9 > .env.release
  docker compose --env-file .env.prod --env-file .env.release \
    -f docker-compose.prod.yml -f docker-compose.release.yml up -d --no-build
  ```

需要在**四个仓库各配一份**的 Secrets / Variables：

| 名称 | 类型 | 说明 |
|---|---|---|
| `DEPLOY_SSH_KEY` | Secret | 部署专用 ed25519 私钥（`ssh-keygen -t ed25519 -C aether-ci-deploy`），不要复用个人密钥 |
| `DEPLOY_HOST`、`DEPLOY_USER`、`DEPLOY_PORT`、`DEPLOY_PATH` | Variables | 部署机地址、SSH 用户、SSH 端口、部署目录（含 `docker-compose.prod.yml` 与 `.env.prod`） |

公钥追加到部署用户的 `~/.ssh/authorized_keys`；`docker-compose.release.yml` 由 aether 的 workflow 随发布同步到 `DEPLOY_PATH`。推、拉都不需要 registry 凭据。

**首次发布是破坏性的**：现有容器跑的是 `aether-admin:prod` 等本地 tag，首次会把 8 个容器全部换成新 tag 的镜像。在维护窗口内做，先记录当前镜像 ID 作为回退目标。

#### 灾备：源码构建（fetch-sources.sh）

- 源码由 `scripts/fetch-sources.sh` 检出到 `AETHER_SOURCE_ROOT`（默认 `.sources/`），Compose 从该目录构建；发布前把 `AETHER_ADMIN_GIT_REF` 等固定到 tag 或提交 SHA，不要长期停留在 `master`，否则无法复现与回滚。脚本会打印每个仓库实际检出的提交 SHA，应记入发布记录。
- **私有仓库凭据走宿主机 git 配置**。不用 BuildKit 的 Git 构建上下文：其 git 源由构建器自行 clone，不读取宿主机的 `credential.helper`、`~/.git-credentials`、`~/.netrc` 与 SSH 私钥，私有仓库必然拉不下来。改为先由脚本用宿主机凭据检出、再让 Compose 从本地目录构建，既不需要额外令牌，也让凭据回到本来生效的位置。部署前可用 `git ls-remote <仓库地址>` 确认宿主机的 git 能直接拉取。
- `fetch-sources.sh` 默认对每个仓库执行 `git clean -ffdx`，会删除未跟踪文件以保证构建上下文与提交严格一致；`AETHER_SOURCE_ROOT` 下的目录由该脚本专用，不要存放其他内容。加 `--no-clean` 可跳过。
- 回滚即把上述 `*_GIT_REF` 改回上一个已知良好版本后重新 `up -d --build`。数据库回滚见本文档「生产切换/回滚」一节。
- 只有 `admin` 执行 Flyway 迁移（`flyway-core` 仅声明在 `admin/pom.xml`），故 admin 保持单副本；若需多副本，副本必须设 `FLYWAY_ENABLED=false`。`front` 依赖 `admin` 健康后才启动，该依赖不可删除。

### 生产注意事项

- **`SPRING_PROFILES_ACTIVE=prod` 是承重配置**：三个 `application.yml` 默认 profile 均为 `dev`，而 dev profile 硬编码 `localhost` 连接地址，容器内必然失败。
- PostgreSQL 必须使用 pgvector 镜像（`V1__init.sql` 会执行 `CREATE EXTENSION vector`），当前固定为 `pgvector/pgvector:pg18`。从 16 升级见上文「PG 16 → 18 迁移」，不是改一行即可。
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
- `api/src/main/resources/application.yml` 被各应用自己的同名文件遮蔽：Spring Boot 解析
  `classpath:/application.yml` 只取第一个命中，即 `admin`/`front` 的 `target/classes` 那份。
  该文件里的 `spring.mvc`、`aether.workflow.*`、`aether.reliability.*` 等配置因此不生效
  （各应用已把需要的那部分抄进自己的 `application.yml`）。**此条为推断，尚未实证**，排期时请先验证。
- **`ConversationSummaryServiceTest#deletionDuringGenerationPreventsSummaryFromBeingWrittenBack` 是
  偶发失败的**，而它属于发布门禁里的阻断用例，意味着发布可能随机中断。2026-09-12 全量跑
  `mvn -B -ntp test` 时命中一次（`Wanted at most 0 times but was 1`），单独重跑该类 4 次全绿。
  根因在 `ConversationSummaryService`：失效判定用毫秒时间戳比较——
  `refreshStartedAt` 在 `refreshAsync` 入口取 `System.currentTimeMillis()`，`evict()` 同样取一次，
  而 `isInvalidatedSince` 判的是 `invalidatedAt > refreshStartedAt`（**严格大于**）。
  两者落在同一毫秒时该次失效对刷新不可见，于是会话已删除、摘要仍被写回（第 265 行附近）。
  Windows 的时钟粒度较粗（约 15ms 一跳），比 Linux runner 更容易撞上。
  **修法不是简单把 `>` 改成 `>=`**：更稳妥的是改用单调递增的世代号（每次 evict 自增）替代
  墙上时钟比较。这是生产并发语义的改动，须单独提一条并补测试。

---

## 数据保留与脱敏

- 工作流终态实例默认保留 90 天：`AETHER_WORKFLOW_SECURITY_RETENTION_DAYS`（0 禁用）。
- 敏感字段脱敏：`AETHER_WORKFLOW_SECURITY_MASK_FIELDS`（默认含 password/secret/token/authorization）。
- 清理 Cron：`AETHER_WORKFLOW_SECURITY_RETENTION_CRON`（默认 `0 30 3 * * ?`）。
