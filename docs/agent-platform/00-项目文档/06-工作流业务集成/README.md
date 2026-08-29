# AI 工作流业务系统接入说明

## 适用范围

业务系统通过服务账号调用已发布的工作流。平台会记录业务关联、保证同一幂等键只创建一个实例，并在实例进入终态后向业务系统发送可验证、可重试的回调。

人工提问与工具确认仍会暂停手动或业务启动的实例；定时触发实例会自动批准其已配置的工具节点。回调只在 `COMPLETED`、
`FAILED`、`TERMINATED` 或 `TIMED_OUT` 时发送。

流程启动、人工回答和失败重试只会完成数据校验与任务入队，不会同步等待模型或远端
MCP。后台持久化工作者会领取任务推进实例；服务重启时超过租约的处理中任务会自动重新领取。

## 部署配置

默认禁止外部回调。生产环境需要配置以下变量：

```env
# 启用业务工作流回调。
AETHER_WORKFLOW_CALLBACK_ENABLED=true
# 接收回调的精确主机名，多个主机使用逗号分隔。
AETHER_WORKFLOW_CALLBACK_ALLOWED_HOSTS=workflow.example.com
# 业务系统与 Aether 共享的 HMAC-SHA256 密钥。
AETHER_WORKFLOW_CALLBACK_SIGNING_SECRET=replace-with-a-long-random-secret
```

可选变量：`AETHER_WORKFLOW_CALLBACK_CONNECT_TIMEOUT_MS`（默认 3000）、`AETHER_WORKFLOW_CALLBACK_READ_TIMEOUT_MS`（默认
10000）、`AETHER_WORKFLOW_CALLBACK_MAX_ATTEMPTS`（默认 8）和 `AETHER_WORKFLOW_CALLBACK_RETRY_INTERVAL_MS`（默认 60000）。

`AETHER_WORKFLOW_EXECUTION_SCAN_INTERVAL_MS` 控制后台执行任务扫描间隔，默认 `1000` 毫秒。它只影响未被即时消费者领取时的恢复速度，不影响已创建实例的可靠性。

`AETHER_WORKFLOW_EXECUTION_MAX_ATTEMPTS` 控制后台任务在数据库、线程池等基础设施异常时的最大自动重试次数，默认 `8`
。超过次数后，任务和实例都会标记为失败并触发终态回调；正常的 Agent/MCP 业务执行错误仍按节点失败规则处理，不会被后台无限重试。

回调 URL 仅允许 `http` 或 `https`，且主机名必须精确匹配 `AETHER_WORKFLOW_CALLBACK_ALLOWED_HOSTS`
；未启用、未配置签名密钥或不在白名单中的地址会被拒绝。

## 启动实例

使用拥有“流程实例可写”权限的业务专用身份令牌调用：

```http
POST /api/agent/workflow/{workflowId}/business-instances
Authorization: Bearer {access-token}
Content-Type: application/json
```

```json
{
  "businessType": "ticket",
  "businessId": "TICKET-20260801-001",
  "idempotencyKey": "ticket:TICKET-20260801-001:created:v1",
  "callbackUrl": "https://workflow.example.com/api/aether/workflow-callback",
  "deadlineAt": 1785593600000,
  "variables": {
    "ticketTitle": "客户无法登录",
    "priority": "P1"
  }
}
```

`businessType`、`businessId`、`idempotencyKey` 为必填项。相同的工作流、服务账号和 `idempotencyKey` 会返回同一个实例
ID，不会重复执行节点。`deadlineAt` 为可选 Unix 毫秒时间；若流程仍在等待人工回答或工具确认，到期后状态会变为 `TIMED_OUT`
并触发回调。该接口拒绝普通用户登录令牌。

成功响应中的 `data` 是工作流实例 ID。业务系统应持久化该 ID，并使用它关联自己的单据。

## 查询与人工恢复

- `GET /api/agent/workflow/instances/{instanceId}`：实例、版本快照、变量和节点执行记录。
- `GET /api/agent/workflow/instances/{instanceId}/callbacks`：回调投递审计和最近错误。
- `POST /api/agent/workflow/instances/{instanceId}/callbacks/{deliveryId}/retry`：人工重投失败的回调（修复业务端配置后使用）。
- `POST /api/agent/workflow/instances/{instanceId}/answer`：提交人工提问答案或工具确认。
- `POST /api/agent/workflow/instances/{instanceId}/retry`：重试当前失败节点。
- `POST /api/agent/workflow/instances/{instanceId}/replay`：从起始节点回放实例；业务系统启动的实例不支持该操作。
- `POST /api/agent/workflow/instances/{instanceId}/terminate`：终止实例。
- `POST /api/agent/workflow/instances/list`：请求体可传 `businessType`、`businessId` 查询当前服务账号创建的实例。

## 服务账号

管理员通过以下接口创建服务账号，并分配最小角色（通常只授予 `/agent/workflow/run` 写权限）。创建和轮换密钥时，`clientSecret`
只在响应中返回一次；平台只保存 BCrypt 哈希。密钥和令牌响应附带 `Cache-Control: no-store`，调用方也不应写入浏览器存储或普通日志。

创建时可配置 `allowedWorkflowIds`（允许启动的工作流 ID 白名单；空数组表示不限制）和 `maxStartsPerHour`（每小时启动额度；`0`
表示不限制）。业务启动前会强制校验二者，超过额度返回 `429`。

- `POST /api/sys/service-account`：创建，参数为 `name`、可选 `clientId`、`description`、`roleIds`。
- `PUT /api/sys/service-account/{id}`：编辑 `name`、`description`、`roleIds`、`allowedWorkflowIds`、`maxStartsPerHour`；客户端
  ID 与密钥不可修改。编辑会清空旧令牌已加载的权限缓存，旧令牌对受权限控制的接口立即失效，需重新签发令牌以应用最新角色权限。
- `DELETE /api/sys/service-account/{id}`：删除服务账号（物理删除账号及关联的底层用户与角色绑定），已签发令牌立即失效。
- `POST /api/sys/service-account/{id}/rotate-secret`：轮换密钥，旧令牌立即失效。
- `POST /api/sys/service-account/{id}/enabled?enabled=false`：禁用或启用；每次状态变化都会使旧令牌失效。
- `POST /api/auth/service-account/token`：使用 client credentials 签发访问令牌，无需用户登录。

令牌请求示例：

```json
{"clientId":"ticket-workflow","clientSecret":"sa_..."}
```

响应中的 `accessToken` 使用 `Authorization: Bearer {accessToken}` 调用业务启动接口。令牌默认有效期为 900 秒，可通过
`AETHER_SERVICE_ACCOUNT_ACCESS_TOKEN_SECONDS` 调整，最大 3600 秒。每次请求都会检查服务账号是否启用且令牌版本是否匹配，因此禁用或轮换密钥无需等待
JWT 自然过期即可生效。

## Webhook 事件触发

管理员可创建绑定工作流和服务账号的触发器：`POST /api/agent/workflow/webhooks`。配置 `businessType`、`businessIdExpression`、
`idempotencyKeyExpression` 及 `variableMapping`。表达式支持 `$body`、`$body.字段路径`、`$header.请求头名`
或字面量；映射结果会作为开始节点变量。

创建响应只返回一次 `signingSecret` 和调用地址 `/api/agent/workflow/webhook/{triggerId}`。事件方必须发送原始 JSON 请求体以及：

- `X-Aether-Webhook-Timestamp`：Unix 毫秒时间戳。
- `X-Aether-Webhook-Signature`：`sha256={Base64(HMAC_SHA256(secret, timestamp + "." + rawBody))}`。

签名使用常量时间比较，并拒绝超过 `AETHER_WORKFLOW_WEBHOOK_SIGNATURE_MAX_AGE_MS`（默认 300000 毫秒）的请求。每个 Webhook
启动实例时也会遵守绑定服务账号的工作流白名单与额度，并通过事件幂等键防止重复启动。

## MCP 写操作幂等

工作流的工具确认节点会向 MCP 服务透传 `X-Aether-Idempotency-Key`，格式稳定为 `workflow:{实例ID}:node:{节点ID}`。流程因网络异常或
Worker 恢复而重试时使用相同的键；接入的 MCP 写工具应按该请求头保存并复用首次结果，避免重复建单、通知或扣费。工具调用审计仍保留每次尝试，便于定位需要人工处理的幂等冲突。

## 运营观测与死信

- `GET /api/agent/workflow/operations/metrics`：返回实例总量、完成率、完成节点平均耗时、人工等待时长、MCP 失败数、回调失败数和执行死信数。
- `GET /api/agent/workflow/operations/dead-letters?limit=50`：返回已达到重试上限的执行任务和回调投递，供 Dashboard
  或外部告警系统轮询处理。

指标聚合直接在 PostgreSQL 执行，不会将所有历史实例加载到应用内存。生产环境可基于 `callbackFailedCount`、
`executionDeadLetterCount` 与 `mcpFailedCount` 配置监控平台阈值告警。

## 数据安全与保留期

节点审计副本和业务回调会按 `AETHER_WORKFLOW_SECURITY_MASK_FIELDS`（默认包含 `password`、`secret`、`token`、`authorization`
等）递归脱敏 JSON 字段，运行时共享变量不受影响。终态实例及关联节点、回调和后台任务默认保留 90 天；通过
`AETHER_WORKFLOW_SECURITY_RETENTION_DAYS` 调整，设为 `0` 可禁用自动清理。清理任务 Cron 由
`AETHER_WORKFLOW_SECURITY_RETENTION_CRON` 配置。

## 回调验签

请求头：

- `X-Aether-Workflow-Event`：`workflow.completed`、`workflow.failed`、`workflow.terminated` 或 `workflow.timed_out`。
- `X-Aether-Workflow-Delivery-Id`：投递唯一 ID，可用于接收端去重。
- `X-Aether-Workflow-Timestamp`：毫秒时间戳。
- `X-Aether-Workflow-Signature`：`sha256={Base64(HMAC_SHA256(secret, timestamp + "." + rawBody))}`。

接收端必须同时验证：主机访问来源、时间戳有效期、HMAC 签名和投递 ID 幂等性。只返回 2xx 表示接收成功；网络异常、408、425、429 和
5xx 会以指数退避进行重试，其他 4xx 会直接标记失败，达到最大次数后保留 `FAILED` 投递审计记录。修复业务端后可通过重投接口恢复，不会回滚工作流本身。

回调正文示例：

```json
{
  "eventType": "workflow.completed",
  "instanceId": "2083...",
  "workflowId": "2083...",
  "workflowVersionId": "2083...",
  "businessType": "ticket",
  "businessId": "TICKET-20260801-001",
  "idempotencyKey": "ticket:TICKET-20260801-001:created:v1",
  "status": "COMPLETED",
  "outputs": {"summary": "..."},
  "errorMessage": null,
  "startedAt": 1785590000000,
  "completedAt": 1785590003000
}
```

`outputs` 只包含发布版本的“最终输出字段”中声明的变量；未声明的流程内部变量、输入变量和工具上下文不会随回调发送。请在工作流编辑页为业务所需的稳定结果字段配置输出契约。
