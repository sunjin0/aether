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
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl front -am -DskipTests dependency:go-offline

COPY common/ common/
COPY api/ api/
COPY storage/ storage/
COPY biz/ biz/
COPY admin/ admin/
COPY front/ front/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B clean package -pl front -am -Dmaven.test.skip=true

FROM eclipse-temurin:11-jre-jammy
WORKDIR /app
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && apt-get install -y --no-install-recommends curl
COPY --from=builder /build/front/target/front-*.jar front.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx512m -Xss512k -XX:MaxMetaspaceSize=192m -XX:MaxDirectMemorySize=128m -XX:ReservedCodeCacheSize=96m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ExitOnOutOfMemoryError -server"
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/v3/api-docs || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar front.jar"]
