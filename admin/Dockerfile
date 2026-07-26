FROM maven:3.8-openjdk-11-slim AS builder
WORKDIR /build
COPY pom.xml ./
COPY common/pom.xml common/
COPY api/pom.xml api/
COPY biz/pom.xml biz/
COPY admin/pom.xml admin/
RUN mvn dependency:go-offline -pl admin -am -q 2>/dev/null || true
COPY common/ common/
COPY api/ api/
COPY biz/ biz/
COPY admin/ admin/
RUN mvn clean package -pl admin -am -DskipTests -q

FROM openjdk:11-jre-slim
WORKDIR /app
COPY --from=builder /build/admin/target/admin-*.jar admin.jar
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
EXPOSE 8080
ENV JAVA_OPTS="-Xmx1g -Xms512m -server"
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar admin.jar"]
