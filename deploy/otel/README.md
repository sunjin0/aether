# OpenTelemetry 接入说明

Aether 的 OTel 导出默认关闭。启用前应准备企业 OTLP Collector，并通过受控网络访问其 OTLP/HTTP endpoint；不要把 endpoint、凭据或业务请求体写入应用日志。

`collector-config.example.yaml` 提供仅接收 OTLP/HTTP Trace/Logs/Metrics、限内存并批量发送到企业后端的模板。使用前必须通过 Secret 管理器注入 `ENTERPRISE_OTLP_ENDPOINT` 与 `ENTERPRISE_OTLP_AUTHORIZATION`，不要将真实值提交到仓库；生产环境还应在入口启用 TLS、来源网段限制和租户级资源属性。

## 服务配置

| 服务 | Trace 开关 | Trace endpoint | Log endpoint | 默认 service name |
| --- | --- | --- | --- | --- |
| Admin | `AETHER_ADMIN_OTEL_ENABLED`（默认关闭） | `AETHER_ADMIN_OTEL_ENDPOINT` | `AETHER_ADMIN_OTEL_LOGS_ENABLED`（默认关闭） / `AETHER_ADMIN_OTEL_LOGS_ENDPOINT` | `AETHER_ADMIN_OTEL_SERVICE_NAME`，默认 `aether-admin` |
| Deep Agent | `AETHER_OTLP_TRACES_ENABLED` | `AETHER_OTLP_TRACES_URL` | `AETHER_OTLP_LOGS_URL` | `aether-deep-agent-service` |
| MCP | `AETHER_OTLP_TRACES_ENABLED` | `AETHER_OTLP_TRACES_URL` | `AETHER_OTLP_LOGS_URL` | `aether-mcp-server` |

Deep Agent 与 MCP 的 Trace/Log exporter 默认关闭；启用时建议同时设置：

```text
AETHER_OTLP_TRACES_ENABLED=true
AETHER_OTLP_TRACES_URL=http://otel-collector:4318/v1/traces
AETHER_OTLP_LOGS_URL=http://otel-collector:4318/v1/logs
OTEL_SERVICE_NAME=<service-specific-name>
```

Admin Trace 启用示例：

```text
AETHER_ADMIN_OTEL_ENABLED=true
AETHER_ADMIN_OTEL_ENDPOINT=http://otel-collector:4318/v1/traces
AETHER_ADMIN_OTEL_SERVICE_NAME=aether-admin
AETHER_ADMIN_OTEL_LOGS_ENABLED=true
AETHER_ADMIN_OTEL_LOGS_ENDPOINT=http://otel-collector:4318/v1/logs
```

Admin 使用标准 OTLP/HTTP exporter，继承入站 `traceparent`，仅记录 HTTP 方法、路径和状态码；不会导出请求体、Prompt、Token、Cookie 或连接器凭据。

服务会继承入站 W3C `traceparent`。Collector 应限制来源网段、启用 TLS（生产环境）并按租户/环境设置资源属性；应用侧仅导出低基数 HTTP 元数据，不导出 Prompt、请求体、Token 或连接器凭据。

## 验收

### 本地观测验收环境

仓库提供 `docker-compose.observability.yml` 作为隔离本地验收栈：Collector 接收 OTLP 4317/4318，Prometheus
采集 Collector 的 9464 指标，Grafana 通过 3000 提供只读浏览入口。该栈不连接企业后端且不包含生产凭据。

```bash
docker compose -f docker-compose.observability.yml config -q
docker compose -f docker-compose.observability.yml up -d
```

演练结束执行 `docker compose -f docker-compose.observability.yml down`；所有服务保留 30 秒 stop grace period。

1. 默认配置启动时不创建 exporter，也不要求 Collector 可达。
2. 启用配置后能在 Collector 中看到 HTTP span 和对应服务日志。
3. 优雅退出或滚动发布时 exporter 完成 flush。
4. 断开 Collector 不影响业务请求主流程，失败由 exporter 自身退避处理。
