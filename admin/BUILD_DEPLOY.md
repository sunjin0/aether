# admin 服务构建与发布命令

每次修改代码后，按以下命令构建并发布（基于 docker-compose，`docker-compose.yml` 只构建/运行 `admin` 服务，配置来自环境变量，默认
`SPRING_PROFILES_ACTIVE=prod`）。

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

> 注意：compose 使用 `container_name: aether-admin` 且加入外部网络 `aether-mcp-server_default`。若存在同名的旧容器（未由
> compose 管理），先删除再执行：`docker rm -f aether-admin`。

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

## 全栈生产部署（PostgreSQL、Redis、Admin、Front、Dashboard、Deep Agent、MCP、Sandbox）

对象存储默认使用阿里云 OSS；内置 MinIO 为 `--profile minio` 兜底。

```sh
Copy-Item .env.prod.example .env.prod
# 编辑 .env.prod，填写全部 replace-with-* 占位值
docker compose --env-file .env.prod -f docker-compose.prod.yml config   # 校验，缺失密钥在此报错
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

发布前验收（从本地工作区构建，覆盖未推送的改动）：

```sh
docker compose -f docker-compose.acceptance.yml up -d
```