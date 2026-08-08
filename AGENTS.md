# AGENTS.md

This file provides guidance to OpenCode when working with code in this repository.

## Project overview

Java 8, Spring Boot 2.7.18, multi-module Maven project. There is no Maven wrapper; commands assume `mvn` is installed and run from the repository root.

Module dependency direction (upstream → downstream):

- `common`: shared infrastructure and utilities (response wrapper, base entity, i18n, exception handling, interceptor, Redis, MyBatis-Plus config, permission AOP, token/AES utilities, validation helpers).
- `api`: contract/data layer. MyBatis-Plus entities, VOs, mapper interfaces, service interfaces, Flyway migrations, i18n/resource YAML files.
- `storage`: MinIO object-storage adapter (`com.aether.storage.service.ObjectStorageService`); depended on by `biz` and `admin`.
- `biz`: business implementation layer. Service implementations extend `ServiceImpl<Mapper, Entity>` and implement interfaces from `api`.
- `admin`: executable Spring Boot admin/API application. REST controllers and `AdminApplication`.
- `front`: executable Spring Boot application shell with `FrontApplication`; no discovered controllers, reuses `biz/common`.

## Common commands

```sh
# Full reactor build
mvn clean package

# Full build without tests
mvn clean package -DskipTests

# Compile only
mvn clean compile

# Build one runnable module and its dependencies
mvn clean package -pl admin -am
mvn clean package -pl front -am

# Build library modules
mvn clean package -pl biz -am
mvn clean package -pl api -am
mvn clean package -pl common -am
```

### Tests

```sh
# Run all tests
mvn test

# Run tests for one module and upstream
mvn test -pl admin -am
mvn test -pl front -am

# Run one admin test class
mvn -pl admin -Dtest=SmsControllerTest test
mvn -pl admin -Dtest=EmailControllerTest test
mvn -pl admin -Dtest=AdminApplicationTests test

# Run one test method
mvn -pl admin -Dtest=SmsControllerTest#count test

# If single-test runs cannot resolve reactor dependencies, install upstream first
mvn -pl admin -am -DskipTests install
mvn -pl admin -Dtest=SmsControllerTest#count test

# Alternative single command through reactor
mvn -pl admin -am -Dtest=SmsControllerTest#count -DfailIfNoTests=false test
```

Tests are Spring Boot context tests under `admin/src/test/java` and `front/src/test/java`; they may load dev-profile configuration and expect local services/configuration to be available.

### Run applications locally

The POMs do **not** configure `spring-boot-maven-plugin`, so prefer direct plugin invocation instead of assuming packaged jars are executable fat jars.

```sh
# Admin app, default port 8080
mvn -pl admin -am -DskipTests install
mvn -pl admin org.springframework.boot:spring-boot-maven-plugin:2.7.18:run -Dspring-boot.run.profiles=dev

# Admin app with port override
mvn -pl admin org.springframework.boot:spring-boot-maven-plugin:2.7.18:run \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments=--server.port=9080

# Front app
mvn -pl front -am -DskipTests install
mvn -pl front org.springframework.boot:spring-boot-maven-plugin:2.7.18:run -Dspring-boot.run.profiles=dev
```

### Static analysis

No Checkstyle/PMD/SpotBugs Maven plugins are configured. Qodana is configured in `qodana.yaml` with `jetbrains/qodana-jvm:2025.1` and project JDK 11.

```sh
qodana scan --linter jetbrains/qodana-jvm:2025.1
```

### Docker / CI

`Jenkinsfile` builds only the `admin` module, then builds `admin/Dockerfile`:

```sh
mvn clean package -pl admin -am
docker build -t admin-service:latest admin/
docker run -d --name admin-container -p 8080:8080 admin-service:latest
```

Caveats: `admin/Dockerfile` copies `admin/target/admin-*.jar`, exposes port `8080`, and runs `java -jar admin.jar`. The container maps host `8080` to container `8080`, matching `admin/src/main/resources/application.yml` (`server.port: 8080`); verify the intended container port before relying on CI/deploy behavior.

Real infra deploys through Docker Compose instead of the single container above:

- `docker-compose.yml`: builds only `admin`, runs with `SPRING_PROFILES_ACTIVE=prod`, and attaches to the external network `aether-mcp-server_default` (`AETHER_SHARED_NETWORK`); all config comes from env vars documented in `.env.example`.
- `docker-compose.all.yml`: full stack (PostgreSQL, Redis, MinIO, admin, dashboard, deep-agent, MCP) built from Git sources; requires a populated `.env.all` (copy `.env.all.example`) with `GIT_AUTH_TOKEN` and production secrets. Host ports are non-default (admin `ADMIN_PORT` 18080).

## Runtime configuration

- `admin/src/main/resources/application.yml`, `front/src/main/resources/application.yml`, and `api/src/main/resources/application.yml` all activate the `dev` profile.
- `admin` explicitly listens on port `8080`; `front` uses Spring Boot's default unless overridden.
- Profile-specific datasource/Redis/mail configuration lives in `api/src/main/resources/application-dev.yml`, `application-test.yml`, and `application-prod.yml` and is placed on the app classpath through module dependencies.
- i18n message bundles are in `api/src/main/resources/i18n/`, with `spring.messages.basename: i18n.api`.
- Runtime infra is PostgreSQL, Redis, and MinIO. Config is env-var driven with local defaults (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `MINIO_*`, `AETHER_DEEP_AGENT_*`, `AETHER_MCP_DELEGATION_SECRET`); see `.env.example`/`.env.all.example`.

## Architecture notes

### HTTP and service layering

- REST controllers are in `admin/src/main/java/com/aether/**/controller` and are grouped by domain (`sys`, `msg`, `user`). They call service interfaces from `api`.
- Service interfaces live in `api/src/main/java/com/aether/**/service`.
- Service implementations live in `biz/src/main/java/com/aether/**/service/impl`.
- New admin endpoints should follow this layering: controller in `admin`, interface/entity/VO/mapper in `api`, implementation in `biz`, shared cross-cutting code in `common`.

### Persistence

- MyBatis-Plus is used through mapper interfaces that extend `BaseMapper<T>`; no mapper XML files were found in source resources.
- Shared entity fields are in `common/src/main/java/com/aether/entity/BaseEntity.java`.
- `common/src/main/java/com/aether/config/MyBatisPlusConfig.java` registers pagination, optimistic-locker, and block-attack interceptors.
- `common/src/main/java/com/aether/config/MyMetaObjectHandler.java` auto-fills common fields such as timestamps, state/deleted flags, and sort number.
- DB schema is managed by Flyway: SQL migrations in `api/src/main/resources/db/migration/postgresql/` (39 `V*.sql`) run automatically on app startup (`out-of-order: false`, `clean-disabled: true`). Change schema by adding a new `V*__*.sql`; do not edit applied migrations or create tables by hand.

### Authentication and permissions

- Login/session behavior is centered in `biz/src/main/java/com/aether/sys/service/impl/UserServiceImpl.java` and exposed by `admin/src/main/java/com/aether/sys/controller/LoginController.java`.
- `common/src/main/java/com/aether/interceptor/GlobalInterceptor.java` reads `Authorization: Bearer ...`, decrypts the token, validates expiry, and stores request user data in `CurrentUser`.
- `common/src/main/java/com/aether/permission/PermissionAspect.java` enforces `@Permission` annotations using a Redis hash keyed by `TokenUtils.TOKEN_KEY` and the current user id.
- The resource model (`sys_resource`) is used both for route trees and permission leaves; read/write access is represented through path-based permission entries.

### Responses, errors, and i18n

- Controllers generally return `common/src/main/java/com/aether/entity/WebResponse.java` via factory methods such as `OK`, `Page`, and `Error`.
- `common/src/main/java/com/aether/exception/GlobalException.java` centralizes exception handling and returns `WebResponse` payloads.
- Locale resolution is handled by `common/src/main/java/com/aether/config/MyLocaleResolver.java`, reading `Accept-Language`.

## Documentation lookup

If a question cannot be answered confidently from the codebase or existing context, read the relevant documents under `docs/` before guessing. `docs/README.md` is the doc index; `docs/superpowers/` holds historical design records and is not a current interface contract.
