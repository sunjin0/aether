# AGENTS.md

## Project overview

This is a Java 17, Spring Boot 2.7.18 multi-module Maven project. Modules are `common` (shared infrastructure), `api` (contracts, entities, mappers and Flyway migrations), `storage` (MinIO adapter), `biz` (business implementations), `admin` (REST application on port 8080), and `front` (Spring Boot shell).

Observability/OTel, Secret Provider, enterprise identity integration, and tenant/workspace/project catalog features have been removed. Do not reintroduce their code, configuration, routes, tables or permissions.

## Build and publish

Use JDK 17. There is no Maven wrapper. Build/test with mvn -pl admin -am test or mvn package.
Only a pushed v* tag publishes this project through .github/workflows/release.yml. All deployment files are in deploy/: compose.yml, runtime Dockerfiles, release.sh, .env.example and README.md. Do not reintroduce root Compose files, source-fetch publishing or alternate deployment pipelines.
Admin/Front are built in CI; only their tagged artifacts are uploaded. PostgreSQL/Redis and the shared network belong to this project; other applications publish from their own repositories. Existing database containers are not recreated by ordinary tag releases. Preserve aether-prod resource names and never use down or --remove-orphans in a project release.

## Configuration

Profile configuration is under `api/src/main/resources/application-*.yml`; the production environment template is deploy/.env.example. Never commit real secrets. Do not add Secret Provider/Vault/Kubernetes, OIDC/SAML/SCIM, OTel/OTLP, Prometheus/Grafana, or retired catalog settings.

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

## Internationalization

All user-facing API response messages, including validation failures, not-found responses, state conflicts, and success notifications, must be resolved with `I18nUtils.getMessage(...)`. Add matching keys to both `api/src/main/resources/i18n/api_zh_CN.properties` and `api/src/main/resources/i18n/api_en_US.properties`; do not return hard-coded natural-language text from controllers or services.

## Status and code presentation

Backend status, enum, and error-code fields must expose stable machine-readable codes or dictionary identifiers. Never use those raw codes as user-facing labels. When an API returns a message for a code, resolve it through `I18nUtils`; frontends must render codes through a dictionary or locale-backed mapping and use a localized fallback rather than displaying an unknown raw code.

## Git commit convention

Use Conventional Commits: `<type>(<scope>): <中文提交描述>`. 类型使用 `feat`、`fix`、`refactor`、`perf`、`docs`、`test`、`build`、`ci` 或 `chore`；scope 使用 `admin`、`api`、`biz`、`db`、`dashboard` 等。提交描述必须使用中文，简洁说明实际变更；提交正文必须说明修改了哪些内容、影响范围、数据库迁移或配置变化，以及必要的验证结果。提交保持单一目的，不混入无关修改。提交前检查 `git diff`，排除密钥和生成文件，并执行相关构建/测试。
