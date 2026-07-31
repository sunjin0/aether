# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-11 AS builder
WORKDIR /build
COPY pom.xml ./
COPY common/pom.xml common/
COPY api/pom.xml api/
COPY storage/pom.xml storage/
COPY biz/pom.xml biz/
COPY admin/pom.xml admin/
COPY front/pom.xml front/
# 依赖只由 POM 决定：代码变更时复用该镜像层和 BuildKit 的 Maven 缓存。
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl admin -am -DskipTests dependency:go-offline

# 依赖缓存预热完成后再复制源码，避免普通代码改动导致重新下载依赖。
COPY common/ common/
COPY api/ api/
COPY storage/ storage/
COPY biz/ biz/
COPY admin/ admin/
COPY front/ front/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -o clean package -pl admin -am -DskipTests

FROM eclipse-temurin:11-jre-jammy
WORKDIR /app
COPY --from=builder /build/admin/target/admin-*.jar admin.jar
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
EXPOSE 8080
ENV JAVA_OPTS="-Xmx1g -Xms512m -server"
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/v2/api-docs || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar admin.jar"]
