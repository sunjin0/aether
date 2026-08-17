# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

This is a Java 8, Spring Boot 2.7.18, multi-module Maven project. There is no Maven wrapper, so commands assume `mvn` is
installed and are run from the repository root.

Module dependency direction:

- `common`: shared infrastructure and utilities: response wrapper, base entity, i18n, exception handling, interceptor,
  Redis config, MyBatis-Plus config, permission AOP, token/AES utilities, validation helpers.
- `api`: contract/data layer. Contains MyBatis-Plus entities, VOs, mapper interfaces, service interfaces, and
  i18n/resource YAML files.
- `biz`: business implementation layer. Service implementations live under
  `biz/src/main/java/com/aether/**/service/impl` and generally extend MyBatis-Plus `ServiceImpl<Mapper, Entity>` while
  implementing interfaces from `api`.
- `admin`: executable Spring Boot admin/API application. Contains the discovered REST controllers and
  `AdminApplication`.
- `front`: executable Spring Boot application shell with `FrontApplication`; at the time this file was written it has no
  discovered controllers and reuses `biz/common`.

## Documentation lookup

If a question cannot be answered confidently from the codebase or existing context, read the relevant documents under
`docs/` before guessing.

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

# Build library modules and dependencies
mvn clean package -pl biz -am
mvn clean package -pl api -am
mvn clean package -pl common -am
```

### Tests

```sh
# Run all tests
mvn test

# Run tests for one module and required upstream modules
mvn test -pl admin -am
mvn test -pl front -am

# Run one admin test class
mvn -pl admin -Dtest=SmsControllerTest test
mvn -pl admin -Dtest=EmailControllerTest test
mvn -pl admin -Dtest=AdminApplicationTests test

# Run one test method
mvn -pl admin -Dtest=SmsControllerTest#count test

# If single-test runs cannot resolve reactor module dependencies, install upstream modules first
mvn -pl admin -am -DskipTests install
mvn -pl admin -Dtest=SmsControllerTest#count test

# Alternative single command through the reactor when upstream modules have no matching test
mvn -pl admin -am -Dtest=SmsControllerTest#count -DfailIfNoTests=false test
```

Existing tests are Spring Boot context tests under `admin/src/test/java` and `front/src/test/java`; they may load
dev-profile configuration and expect local services/configuration to be available.

### Run applications locally

The POMs do not configure `spring-boot-maven-plugin`, so prefer direct plugin invocation instead of assuming the
packaged jars are executable Spring Boot fat jars.

```sh
# Admin app, default port 8080
mvn -pl admin -am -DskipTests install
mvn -pl admin org.springframework.boot:spring-boot-maven-plugin:2.7.18:run -Dspring-boot.run.profiles=dev

# Admin app with a port override
mvn -pl admin org.springframework.boot:spring-boot-maven-plugin:2.7.18:run \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments=--server.port=9080

# Front app
mvn -pl front -am -DskipTests install
mvn -pl front org.springframework.boot:spring-boot-maven-plugin:2.7.18:run -Dspring-boot.run.profiles=dev
```

### Static analysis

No Checkstyle/PMD/SpotBugs Maven plugins are configured. Qodana is configured in `qodana.yaml` with
`jetbrains/qodana-jvm:2025.1` and project JDK 11.

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

Caveats: `admin/Dockerfile` copies `admin/target/admin-*.jar`, exposes port `8080`, and runs `java -jar admin.jar`. The
container maps host `8080` to container `8080`, matching `admin/src/main/resources/application.yml` (
`server.port: 8080`); verify the intended container port before relying on CI/deploy behavior.

## Runtime configuration

- `admin/src/main/resources/application.yml`, `front/src/main/resources/application.yml`, and
  `api/src/main/resources/application.yml` all activate the `dev` profile.
- `admin` explicitly listens on port `8080`; `front` uses Spring Boot's default unless overridden.
- Profile-specific datasource/Redis/mail configuration lives in `api/src/main/resources/application-dev.yml`,
  `application-test.yml`, and `application-prod.yml` and is placed on the app classpath through module dependencies.
- i18n message bundles are in `api/src/main/resources/i18n/`, with `spring.messages.basename: i18n.api`.

## Architecture notes

### HTTP and service layering

- REST controllers are in `admin/src/main/java/com/aether/**/controller` and are grouped by domain (`sys`, `msg`,
  `user`). They call service interfaces from `api`.
- Service interfaces live in `api/src/main/java/com/aether/**/service`.
- Service implementations live in `biz/src/main/java/com/aether/**/service/impl`.
- New admin endpoints should follow this layering: controller in `admin`, interface/entity/VO/mapper in `api`,
  implementation in `biz`, shared cross-cutting code in `common`.

### Persistence

- MyBatis-Plus is used through mapper interfaces that extend `BaseMapper<T>`; no mapper XML files were found in source
  resources.
- Shared entity fields are in `common/src/main/java/com/aether/entity/BaseEntity.java`.
- `common/src/main/java/com/aether/config/MyBatisPlusConfig.java` registers pagination, optimistic-locker, and
  block-attack interceptors.
- `common/src/main/java/com/aether/config/MyMetaObjectHandler.java` auto-fills common fields such as timestamps,
  state/deleted flags, and sort number.

### Authentication and permissions

- Login/session behavior is centered in `biz/src/main/java/com/aether/sys/service/impl/UserServiceImpl.java` and exposed
  by `admin/src/main/java/com/aether/sys/controller/LoginController.java`.
- `common/src/main/java/com/aether/interceptor/GlobalInterceptor.java` reads `Authorization: Bearer ...`, decrypts the
  token, validates expiry, and stores request user data in `CurrentUser`.
- `common/src/main/java/com/aether/permission/PermissionAspect.java` enforces `@Permission` annotations using a Redis
  hash keyed by `TokenUtils.TOKEN_KEY` and the current user id.
- The resource model (`sys_resource`) is used both for route trees and permission leaves; read/write access is
  represented through path-based permission entries.

### Responses, errors, and i18n

- Controllers generally return `common/src/main/java/com/aether/entity/WebResponse.java` via factory methods such as
  `OK`, `Page`, and `Error`.
- `common/src/main/java/com/aether/exception/GlobalException.java` centralizes exception handling and returns
  `WebResponse` payloads.
- Locale resolution is handled by `common/src/main/java/com/aether/config/MyLocaleResolver.java`, reading
  `Accept-Language`.
