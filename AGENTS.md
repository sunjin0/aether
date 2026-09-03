# AGENTS.md

## Project overview

This is a Java 17, Spring Boot 2.7.18 multi-module Maven project. Modules are `common` (shared infrastructure), `api` (contracts, entities, mappers and Flyway migrations), `storage` (MinIO adapter), `biz` (business implementations), `admin` (REST application on port 8080), and `front` (Spring Boot shell).

Observability/OTel, Secret Provider, enterprise identity integration, and tenant/workspace/project catalog features have been removed. Do not reintroduce their code, configuration, routes, tables or permissions.

## Build and publish

Use JDK 17. There is no Maven wrapper.

```sh
mvn clean package
mvn -pl admin -am -DskipTests compile
mvn -pl admin -am test
docker compose build --pull=false admin
docker compose up -d --remove-orphans admin
docker compose ps admin
```

`admin/Dockerfile` uses Maven/Temurin 17 and produces `admin.jar`. Expected endpoint: `http://localhost:8080`. `docker-compose.yml` builds admin only; `docker-compose.all.yml` is the optional full stack.

## Configuration

Profile configuration is under `api/src/main/resources/application-*.yml`; environment templates are `.env.example` and `.env.all.example`. Never commit real secrets. Do not add Secret Provider/Vault/Kubernetes, OIDC/SAML/SCIM, OTel/OTLP, Prometheus/Grafana, or retired catalog settings.

## Persistence

PostgreSQL schema and cleanup are managed by Flyway under `api/src/main/resources/db/migration/postgresql/`. Applied migrations are immutable. All schema or data changes must be a new forward-only `V*__description.sql`; never edit old migrations, manually alter production tables, or use Flyway clean. Permission data is stored in `sys_resource` and `sys_role_resource`; remove resource records and role grants together when retiring a feature.

## Architecture

- Controllers: `admin/src/main/java/com/aether/**/controller`
- Contracts/entities/mappers/interfaces: `api`
- Implementations: `biz`
- Shared infrastructure: `common`
- Responses use `WebResponse`; authentication uses bearer token plus `CurrentUser`; authorization uses `@Permission` and `sys_resource`.
- Preserve dependency direction: `common -> api -> storage/biz -> admin`.

## Change hygiene

Use `rg` for discovery and `apply_patch` for edits. Search code, YAML, Compose files and migration history when changing a feature. Preserve unrelated user changes, run proportional verification, and check admin container health after publishing.

## Git commit convention

Use Conventional Commits: `<type>(<scope>): <中文提交描述>`. 类型使用 `feat`、`fix`、`refactor`、`perf`、`docs`、`test`、`build`、`ci` 或 `chore`；scope 使用 `admin`、`api`、`biz`、`db`、`dashboard` 等。提交描述必须使用中文，简洁说明实际变更；提交正文必须说明修改了哪些内容、影响范围、数据库迁移或配置变化，以及必要的验证结果。提交保持单一目的，不混入无关修改。提交前检查 `git diff`，排除密钥和生成文件，并执行相关构建/测试。
