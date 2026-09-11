# syntax=docker/dockerfile:1
# 发布态运行镜像。jar 由 CI 在 GitHub Actions 里构建完成后随暂存区上传，
# 本文件只负责装 curl（供 compose 的健康检查调用）并把 jar 放进镜像。
#
# 与源码构建路径 admin/Dockerfile 并存、互不影响：那个用于离线灾备
# （scripts/fetch-sources.sh + docker-compose.prod.yml），本文件用于 docker-compose.release.yml。
#
# 构建上下文约定为 release/<tag>/admin，其中必须存在 admin.jar。
# JAVA_OPTS 与 HEALTHCHECK 与 admin/Dockerfile 保持一致，避免两种路径下运行时行为漂移。
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# curl 仅供运行时健康检查使用。放在复制业务 Jar 之前，并缓存 apt 索引与软件包：
# 业务 jar 变更导致镜像层失效时，无需重新下载 apt 依赖。
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && apt-get install -y --no-install-recommends curl
COPY admin.jar admin.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx512m -Xss512k -XX:MaxMetaspaceSize=192m -XX:MaxDirectMemorySize=128m -XX:ReservedCodeCacheSize=96m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ExitOnOutOfMemoryError -server"
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/v3/api-docs || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar admin.jar"]
