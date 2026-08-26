# Aether Agent 中台开放 API v1

所有接口使用服务账号令牌：`Authorization: Bearer <accessToken>`。服务账号只能访问其所属业务应用空间，且必须已被授权对应的 Agent 或工作流。

## 获取令牌

`POST /api/auth/service-account/token`

```json
{"clientId":"erp_prod","clientSecret":"sa_xxx"}
```

令牌有效期由平台返回的 `expiresIn` 指定；不要在浏览器、本地存储或日志中保存 `clientSecret`。

## 发现已发布能力

`GET /openapi/v1/capabilities`

仅返回当前应用空间已发布的智能客服、智能问答或业务助手产品，以及冻结版本的输入/输出 Schema。业务方应按 Schema 构造请求，不能依赖自然语言中的隐式格式。

## 启动工作流

`POST /openapi/v1/workflows/runs`

```json
{
  "workflowCode":"purchase_approval",
  "businessId":"PO-20260826-001",
  "businessType":"purchase_order",
  "idempotencyKey":"erp:PO-20260826-001:create",
  "callbackUrl":"https://erp.example.com/aether/callback",
  "input":{"amount":1200,"currency":"CNY"}
}
```

写请求应通过 `Idempotency-Key` 请求头传递幂等键（旧版请求体 `idempotencyKey` 仍兼容，且请求头优先）。相同工作流和业务幂等键重复提交只创建一个运行。响应中的 `runId` 可用于查询与取消。

## 查询与取消工作流

- `GET /openapi/v1/workflows/runs/{runId}`
- `POST /openapi/v1/workflows/runs/{runId}/cancel`

仅拥有该运行服务账号身份的调用方可查询或取消；平台不会返回节点定义、内部工具参数、模型密钥或未脱敏错误。

## 同步 Agent 问答

`POST /openapi/v1/agents/chat`

```json
{
  "agentCode":"hr_knowledge_qa",
  "conversationId":"optional-existing-conversation-id",
  "businessId":"EMP-1001",
  "idempotencyKey":"portal:EMP-1001:question:42",
  "input":"年假如何计算？"
}
```

`Idempotency-Key` 请求头为必填（旧版 `idempotencyKey` 字段仍兼容）。相同“应用空间 + Agent + 幂等键”在 24 小时内返回首次安全结果；同时执行则返回 `409`，调用方应稍后重试。失败响应不会缓存。

响应仅包含 `conversationId`、`answer`、`citations`、`runId`、标准交互状态和 `traceId`；不会返回推理过程、工具调用参数、内部请求头、模型原始响应或凭据。

## 异步 Agent 任务

- `POST /openapi/v1/agents/runs`
- `GET /openapi/v1/agents/runs/{runId}`
- `POST /openapi/v1/agents/runs/{runId}/cancel`

提交请求与同步问答使用相同的 `agentCode`、`conversationId`、`businessId`、`input` 和可选 `context` 字段。`Idempotency-Key` 请求头必填；相同应用空间、Agent 和幂等键只会创建一个运行。接口同时支持标准 Agent 与 Deep Agent，响应返回 `runId`、`conversationId`、`businessId`、`status` 和 `traceId`。

查询仅允许当前应用空间访问。终态成功时才返回安全的 `answer`，失败时仅返回稳定的 `errorCode`，不会暴露内部异常、工具参数、模型原始响应或凭据。可取消的排队或运行中任务会转换为 `CANCELLED`。

## 回调验签与补偿

工作流终态会以 `run.succeeded`、`run.failed` 或 `run.cancelled` 投递。请求头包含：

- `X-Aether-Workflow-Event`
- `X-Aether-Workflow-Delivery-Id`
- `X-Aether-Workflow-Timestamp`
- `X-Aether-Workflow-Signature: sha256=<hex>`

验签原文是 `timestamp + "." + requestBody`，使用平台与业务系统约定的 HMAC-SHA256 密钥。业务方须校验时间窗口、保存 delivery ID 以防重放，并以 `2xx` 确认接收。非 `2xx` 和网络故障会由平台重试；最终失败后可通过运行查询接口补偿。

回调和查询结果都不包含模型密钥、SMTP 授权码、内部凭据令牌、原始工具协议或未脱敏个人数据。
