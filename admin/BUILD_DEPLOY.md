# admin 服务构建与发布命令

每次修改代码后，按以下命令构建并发布（基于 docker-compose，`docker-compose.yml` 只构建/运行 `admin` 服务，配置来自环境变量，默认 `SPRING_PROFILES_ACTIVE=prod`）。

## 1. 构建镜像（构建并重新打包 jar）

在仓库根目录执行：

```sh
docker compose -f docker-compose.yml build admin
```

等价于 `mvn clean package -pl admin -am -DskipTests` + `docker build`，产物为 `aether-admin:latest` 镜像。

## 2. 发布（启动/重建容器）

```sh
docker compose -f docker-compose.yml up -d admin
```

> 注意：compose 使用 `container_name: aether-admin` 且加入外部网络 `aether-mcp-server_default`。若存在同名的旧容器（未由 compose 管理），先删除再执行：`docker rm -f aether-admin`。

查看日志与状态：

```sh
docker compose -f docker-compose.yml logs -f admin
docker compose -f docker-compose.yml ps
```

## 3. 本地调试运行（不走 Docker）

```sh
mvn -pl admin -am -DskipTests install
mvn -pl admin org.springframework.boot:spring-boot-maven-plugin:2.7.18:run -Dspring-boot.run.profiles=dev
```

## 全栈一键部署（PostgreSQL、Redis、MinIO、Admin、Dashboard、Deep Agent、MCP）

```sh
Copy-Item .env.all.example .env.all
# 编辑 .env.all，至少设置 GIT_AUTH_TOKEN 及生产密钥
docker compose --env-file .env.all -f docker-compose.all.yml -p aether up -d --build
```