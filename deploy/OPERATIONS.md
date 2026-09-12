# Agent 平台 — 运维手册

> 更新日期：2026-09-12

---

生产排查命令针对已成功发布的 aether tag，先在服务器准备：

    cd "$DEPLOY_PATH"
    tag="$(cat .releases/aether.tag)"
    export AETHER_RELEASE_TAG="$tag"
    export AETHER_RELEASE_ROOT="$PWD/release/$tag"
    RELEASE_COMPOSE="$AETHER_RELEASE_ROOT/admin/deploy/compose.yml"

首次发布配置和 tag 操作见 [README.md](README.md)。数据库维护是单独运维操作，不是另一条应用发布流水线。

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

镜像串与 `.github/workflows/release.yml`、`deploy/compose.yml`、`deploy/.env.example` 四处
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

> ⚠️ **两处必须同时改。只改挂载点、忘了改部署机 `deploy/.env` 里的 `POSTGRES_IMAGE`，
> 结果是静默起一个空库——这是本次改动最危险的一种失败。**
> 已实测（2026-09-12，pg16 镜像 + 新挂载点 + 卷根放着 PG16 集群的卷）：pg16 镜像会
> 在 `<卷>/data` 上 `initdb` 出一个全新空集群，容器状态 `running`、日志正常打出
> `database system is ready to accept connections`，而真实集群原封不动躺在卷根。
> 应用照常启动、admin 照常跑完 209 个迁移——**表面全绿，数据却是空的**。原因是
> pg16 镜像的 `PGDATA` 仍是 `/var/lib/postgresql/data`，改挂载点只是让这个路径落到
> 卷内一个不存在的子目录上，entrypoint 见其为空便当作全新实例初始化。
> 所以升级前的第一步是先确认镜像渲染结果，别只看 compose 文件：
>
> ```sh
> docker compose --env-file deploy/.env -f "$RELEASE_COMPOSE" config | grep -A2 'postgres:' | grep image
> # 必须是 pgvector/pgvector:pg18；若仍是 pg16 说明 deploy/.env 覆盖了默认值
> ```
>
> 走发布流程时这条已由 workflow 的预检自动断言（不符即中止，见「打 tag 自动发布」的预检门禁）；
> 手工升级仍要自己先确认。

**两处都改对之后，失败形态才是响亮的**：`postgres-data` 卷里是 PG16 集群，其数据就在卷根，
而 pg18 的 entrypoint 会扫描 `/var/lib/postgresql`、`/var/lib/postgresql/data`、
`/var/lib/postgresql/*/docker` 找 `PG_VERSION`，命中后调用 `docker_error_old_databases`
并 `exit 1`。表现为容器反复重启（`docker logs aether-postgres` 会明确打印检测到旧数据目录），
应用侧则是连不上库。所以**别指望「改完起不来」是配置写错了**，它就是迁移没做。

维护窗口内的步骤：

```sh
cd "$DEPLOY_PATH"
mkdir -p backup
set -a; . ./deploy/.env; set +a
COMPOSE="docker compose --env-file deploy/.env -f "$RELEASE_COMPOSE""

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

# 5) 确认 deploy/.env 的 POSTGRES_IMAGE 已是 pg18（挂载点已在 compose 里改好），起空库
grep '^POSTGRES_IMAGE=' deploy/.env
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
> 不需要密码；`set -a` 载入 `deploy/.env` 是为了拿到正确的角色名与库名。

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
docker compose --env-file deploy/.env -f "$RELEASE_COMPOSE" ps
docker logs --tail 300 aether-admin
docker logs --tail 300 aether-deep-agent
```

Admin 容器健康检查使用 `/actuator/health`（`admin/src/main/resources/application.yml` 暴露 `health,info`，且 `show-details: never`，返回成功但不泄露组件详情）。该路径无需鉴权：`GlobalFilter` 仅在请求带 `Authorization` 头时才校验令牌。

> 早期版本的本手册曾要求改用 `/v2/api-docs` 并称「勿直接用 Actuator」，此为错误记载：prod profile 下 `knife4j.enable: false`，Springfox 不会 bootstrap，`/v2/api-docs` 实际返回 404。

---

## Tag 发布与回滚

所有项目使用各自 deploy/README.md 中的 tag 流程。此目录 [README.md](README.md) 是 aether 唯一发布说明；不再提供源码检出、Jenkins 或本地 Compose 发布入口。

## 数据保留与脱敏

- 工作流终态实例默认保留 90 天：`AETHER_WORKFLOW_SECURITY_RETENTION_DAYS`（0 禁用）。
- 敏感字段脱敏：`AETHER_WORKFLOW_SECURITY_MASK_FIELDS`（默认含 password/secret/token/authorization）。
- 清理 Cron：`AETHER_WORKFLOW_SECURITY_RETENTION_CRON`（默认 `0 30 3 * * ?`）。
