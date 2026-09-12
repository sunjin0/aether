# syntax=docker/dockerfile:1
# Tag 运行镜像；JAR 由 GitHub Actions 打包。
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
