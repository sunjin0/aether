# 使用官方 OpenJDK 镜像作为基础镜像
FROM openjdk:11-jre-slim

# 设置工作目录
WORKDIR /app

# 从构建上下文复制 jar 包到容器中
COPY target/admin-*.jar admin.jar

# 暴露 Web 服务端口
EXPOSE 8080

# 设置 JVM 参数
ENV JAVA_OPTS="-Xmx1g -Xms512m -server"

# 添加健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 设置容器启动时执行的命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar admin.jar"]
