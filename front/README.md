# Front 外部接入 API 使用文档

`front` 模块用于承载外部系统通过服务账号调用 Agent 和工作流的业务接口。服务账号是独立凭证主体，不属于后台管理员账号体系；后台管理员只在 `admin` 模块中负责创建服务账号、配置可调用资源、签发或轮换令牌。

## 部署

本地 compose 已包含独立 `front` 服务：

```sh
docker compose build front
docker compose up -d front
```

默认端口映射为：

```yaml
${FRONT_PORT:-8081}:8080
```

如果宿主机 `8081` 被占用，可以覆盖端口：

```sh
FRONT_PORT=18082 docker compose up -d front
```

容器名和网络别名均为 `aether-front`。全量部署文件 `docker-compose.all.yml` 也包含 `front` 服务。

## 鉴权

该接口由 **Front 服务**提供，不经过 Admin 后台。外部系统通过服务账号的 `clientId/clientSecret` 换取访问令牌：

```http
POST /api/auth/service-account/token
Content-Type: application/json

{
  "clientId": "sa_order_service",
  "clientSecret": "创建或轮换时获取的明文密钥"
}
```

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `clientId` | 是 | 后台创建服务账号时生成或指定的客户端 ID。 |
| `clientSecret` | 是 | 创建或轮换密钥时仅展示一次的明文密钥；不能放入 URL、浏览器或日志。 |

成功响应的 `data`：

```json
{
  "accessToken": "<encrypted-access-token>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

`expiresIn` 单位为秒。认证失败、账号被禁用或密钥已轮换时请求失败，不能重试旧令牌。

后续调用 Front 业务接口时携带：

```http
Authorization: Bearer <accessToken>
```

服务账号令牌只能访问 `/api/business/**` 和令牌签发接口。访问 `/api/sys/**`、`/api/agent/**` 等后台管理接口会返回 `403`。

## 通用响应

普通 JSON 接口使用统一响应包装：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

实际字段以 `data` 为准。`SSE` 接口直接返回 `text/event-stream`，不使用统一 JSON 包装。

## Agent 接口

### 查询可调用 Agent

```http
GET /api/business/agents
Authorization: Bearer <accessToken>
```

只返回当前服务账号被显式授权的已发布产品所关联、且仍启用的 Agent。

### 异步执行 Agent

```http
POST /api/business/agents/{agentId}/runs
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "message": "请审核这份合同的付款条款",
  "conversationId": "optional-conversation-id",
  "idempotencyKey": "order-20260821-0001",
  "variables": {
    "contractId": "C-10001"
  },
  "metadata": {
    "source": "erp"
  }
}
```

请求字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `message` | 是 | 本次 Agent 输入 |
| `conversationId` | 否 | 继续同一服务账号主体下的会话；为空则创建新会话 |
| `idempotencyKey` | 否 | 幂等键；同一服务账号、同一 Agent、同一键会返回同一个 `runId` |
| `variables` | 否 | 业务变量快照 |
| `metadata` | 否 | 调用方自定义元数据 |

返回 `data`：

```json
{
  "runId": "run-id",
  "agentId": "agent-id",
  "conversationId": "conversation-id",
  "status": "QUEUED",
  "output": null,
  "errorMessage": null,
  "createdAt": 1787300000000,
  "updatedAt": 1787300000000
}
```

`status` 常见值：

| 状态 | 说明 |
| --- | --- |
| `QUEUED` | 已提交或等待处理 |
| `RUNNING` | 执行中 |
| `SUCCEEDED` | 执行成功 |
| `FAILED` | 执行失败 |

### 流式执行 Agent

```http
POST /api/business/agents/{agentId}/stream
Authorization: Bearer <accessToken>
Content-Type: application/json
Accept: text/event-stream

{
  "message": "请审核这份合同的付款条款",
  "conversationId": "optional-conversation-id",
  "variables": {
    "contractId": "C-10001"
  },
  "metadata": {
    "source": "erp"
  }
}
```

SSE 事件：

| 事件 | 说明 |
| --- | --- |
| `accepted` | `DEEP` Agent 已创建运行，包含 `runId/conversationId` |
| `status` | 阶段状态 |
| `reasoning` | 推理内容增量 |
| `message` | 模型回复增量 |
| `tool_call` | 工具调用信息 |
| `question` | Agent 需要人工输入 |
| `run_step` | `DEEP` Agent 持久化运行步骤 |
| `done` | 执行结束 |
| `error` | 执行失败或鉴权、权限、额度错误 |

SSE 示例：

```text
event: accepted
data: {"runId":"run-id","conversationId":"conversation-id"}

event: message
data: {"conversationId":"conversation-id","content":"付款条款存在..."}

event: done
data: {"runId":"run-id","agentId":"agent-id","status":"SUCCEEDED"}
```

### 查询 Agent 运行

```http
GET /api/business/agents/runs/{runId}
Authorization: Bearer <accessToken>
```

返回 `data`：

```json
{
  "runId": "run-id",
  "agentId": "agent-id",
  "conversationId": "conversation-id",
  "status": "SUCCEEDED",
  "output": "审核结论...",
  "errorMessage": null,
  "createdAt": 1787300000000,
  "updatedAt": 1787300010000
}
```

只能查询同一个服务账号主体创建的运行记录。

## 工作流接口

### 查询可启动工作流

```http
GET /api/business/workflows
Authorization: Bearer <accessToken>
```

只返回当前服务账号被显式授权的已发布产品所关联、且仍处于已发布状态的工作流。

### 启动工作流

```http
POST /api/business/workflows/{workflowId}/instances
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "businessType": "contract",
  "businessId": "C-10001",
  "idempotencyKey": "contract-C-10001-submit",
  "callbackUrl": "https://example.com/aether/workflow/callback",
  "deadlineAt": 1787386400000,
  "variables": {
    "contractId": "C-10001",
    "amount": 120000
  }
}
```

请求字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `variables` | 否 | 工作流变量 |
| `businessType` | 否 | 调用方业务类型 |
| `businessId` | 否 | 调用方业务 ID |
| `idempotencyKey` | 否 | 建议每个业务事件稳定唯一 |
| `callbackUrl` | 否 | 工作流回调地址 |
| `deadlineAt` | 否 | 人工等待 SLA 截止时间，Unix 毫秒 |

返回 `data` 为实例 ID：

```json
"workflow-instance-id"
```

### 查看工作流实例

```http
GET /api/business/workflows/instances/{instanceId}
Authorization: Bearer <accessToken>
```

返回 `data` 为工作流实例详情，包含实例基础字段、`workflowName`、节点实例列表 `nodes`、版本快照 `versionNodes/versionEdges` 等。

只能查看同一个服务账号主体创建的实例。

### 订阅工作流实例事件

```http
GET /api/business/workflows/instances/{instanceId}/events
Authorization: Bearer <accessToken>
Accept: text/event-stream
```

订阅成功后会先推送当前实例快照：

```text
event: instance.status
data: {"id":"workflow-instance-id","workflowName":"合同审批流程","status":1}
```

后续事件由工作流运行时推送，事件名和数据结构以运行时节点事件为准。

## 错误与限制

| HTTP 状态 | 场景 |
| --- | --- |
| `401` | 未携带 token、token 过期或签名无效 |
| `403` | 服务账号无权限访问该 Agent/工作流，或访问了非 `/api/business/**` 接口 |
| `404` | Agent、工作流、运行或实例不存在 |
| `422` | 请求参数缺失，例如 Agent 调用未提供 `message` |
| `429` | 超过服务账号小时调用额度 |

服务账号权限在 `admin` 后台配置：

- `allowedAgentIds` 控制可调用 Agent。
- `maxAgentCallsPerHour` 控制 Agent 小时调用额度，`0` 表示不限制。
- `allowedWorkflowIds` 控制可启动工作流。
- `maxStartsPerHour` 控制工作流小时启动额度，`0` 表示不限制。

## curl 示例

```sh
TOKEN="your-access-token"
FRONT_BASE_URL="http://localhost:18082"

curl -N \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"message":"请总结这条业务记录","idempotencyKey":"demo-001"}' \
  "${FRONT_BASE_URL}/api/business/agents/agent-id/stream"

```
