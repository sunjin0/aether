# 对话 Agent 产品化方案

> 状态：已实施的产品化基线（本文同时作为后续兼容演进约束）
>
> 目标：将已配置的 Agent 以稳定、可授权、可版本化的对话产品形式提供给内部业务系统；典型场景包括智能客服、知识问答和坐席辅助。

## 1. 结论

平台只对外提供两种固定产品协议：

| 产品类型 | 面向场景 | 输入 | 输出 | 契约来源 |
| --- | --- | --- | --- | --- |
| `AGENT` | 客服、问答、助手等多轮对话 | 自然语言消息 | 自然语言回答及标准交互事件 | 平台统一协议 |
| `WORKFLOW` | 审批、自动化和跨系统任务 | JSON 变量 | JSON 变量和异步回调 | 每个工作流发布版本的 Schema |

`AGENT` 产品不定义独立的输入/输出 Schema，不要求模型返回业务 JSON。所有 Agent 产品共用对话协议；不同 Agent 仅在能力和业务语义上不同。

产品不是 Agent 的副本。产品只负责将一个已配置 Agent 发布为对外可调用的版本，并提供业务应用空间隔离、服务账号授权、生命周期和运行追溯。

## 2. 对象与职责

### 2.1 Agent 定义

Agent 定义是内部可复用的能力单元，负责回答"如何完成对话"：

- 模型、温度、最大 Token、标准或 Deep 执行模式。
- 系统提示词、角色、回复风格、安全边界。
- Skill、知识库、MCP/平台工具及其调用策略。
- 工具确认、人工交互、引用和记忆能力。

现有 `agent_definition` 已承担模型、提示词、执行模式等核心配置。工具、技能和知识库的绑定继续归属 Agent 定义，不复制到产品。

### 2.2 产品

产品是对外发布与治理单元，负责回答"谁可以以什么稳定入口调用此能力"：

- 产品名称、编码、说明和业务应用空间。
- 目标类型及目标 ID：`AGENT + agentDefinitionId` 或 `WORKFLOW + workflowId`。
- 逻辑产品 ID、版本号、草稿/发布/停止/删除生命周期。
- 发布时的目标配置快照。
- 服务账号对具体已发布产品版本的授权。

产品不重新定义模型、Prompt、知识库、工具、技能或普通回复格式。否则 Agent 和产品会形成两个配置真相来源。

### 2.3 服务账号

服务账号是业务系统的机器身份：

- 仅能访问所属业务应用空间。
- 仅能调用显式授权的具体产品版本。
- 不允许以 Agent ID、Agent Code、工作流 ID 绕过产品授权。
- 服务账号令牌绝不下发到浏览器或终端客户。

### 2.4 业务系统

业务系统是客户身份、渠道和业务数据的权威来源：

- 维护外部会话与平台会话的映射。
- 持有服务账号并转发客户消息到平台。
- 接收文本回答和标准交互事件后，驱动渠道展示、转人工或业务流程。
- 对订单、账户、退款等数据的最终授权与写操作负责。

## 3. 对话 Agent 统一协议

### 3.1 同步问答

接口：`POST /openapi/v1/agents/chat`

请求头：

```http
Authorization: Bearer <service-account-access-token>
Idempotency-Key: customer-message-123
Content-Type: application/json
```

请求体：

```json
{
  "productCode": "mall-customer-service",
  "conversationId": "optional-platform-conversation-id",
  "businessId": "optional-business-reference",
  "input": "我的订单为什么还没有发货？",
  "context": {
    "customerId": "cust_10086",
    "channel": "web",
    "externalConversationId": "web-session-789"
  }
}
```

字段约束：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `productCode` | 是 | 已发布、启用且服务账号已授权的 Agent 产品版本编码。 |
| `conversationId` | 否 | 平台会话 ID。首次调用不传；后续同一外部会话必须复用。 |
| `businessId` | 否 | 业务系统的关联号，如工单号、客户请求号。 |
| `input` | 是 | 客户的自然语言消息。 |
| `context` | 否 | 受控业务元数据，不是 Prompt 内容。 |

`productCode` 必须唯一定位一个可调用的产品版本。能力发现接口同时返回 `productId`、`productProfileId`、`versionNo`、`code` 和弃用时间，接入方不得根据名称推断版本。若后续采用稳定编码，必须显式增加 `version` 字段；不得同时把同一个 `code` 解释为逻辑产品和具体版本。

成功响应：

```json
{
  "code": 0,
  "data": {
    "conversationId": "2092800000000000001",
    "answer": "抱歉让您久等了。我正在为您查询订单状态。",
    "citations": [],
    "runId": "2092800000000000002",
    "interactionStatus": null,
    "interactionType": null,
    "traceId": "0af7651916cd43dd8448eb211c80319c"
  }
}
```

响应约束：

- `answer` 始终是面向终端用户的纯文本或 Markdown，不是推理过程、工具参数或内部 JSON。
- `citations` 是可选知识引用，由业务系统决定是否展示给客户。
- `interactionStatus` 和 `interactionType` 是机器可读的标准交互信号；业务系统不得通过解析 `answer` 猜测状态。
- `traceId` 用于业务系统和平台联合排障。

成功响应和错误响应均须在 `conversation-api-v1` 中发布 JSON Schema。`citations` 固定为数组（每项至少含 `sourceId`、`title`、`uri` 和可选 `snippet`）；`interactionData` 为对象，且按 `interactionType` 校验，不能以字符串 JSON 或未定义字段替代。

### 3.2 异步运行

接口：

- `POST /openapi/v1/agents/runs`
- `GET /openapi/v1/agents/runs/{runId}`
- `POST /openapi/v1/agents/runs/{runId}/cancel`

异步接口使用与同步问答相同的请求字段和回答语义。用于 Deep Agent、耗时检索或不适合阻塞业务请求的场景。终态成功时返回 `answer`；失败时返回稳定错误码，不能泄露模型原始响应或内部异常。

运行创建时必须记录 `productProfileId`、产品快照 ID 和 `serviceAccountId`。查询和取消仅允许创建该运行的服务账号，或被显式授予该具体产品版本的服务账号访问；仅按 `applicationId` 校验不足以隔离运行数据。

### 3.3 交互恢复

对需要业务系统继续处理的交互，提供以下固定端点：

- `POST /openapi/v1/agents/runs/{runId}/interactions/{interactionId}/submit`：提交 `USER_INPUT_REQUIRED` 的补充输入。
- `POST /openapi/v1/agents/runs/{runId}/interactions/{interactionId}/confirm`：提交 `USER_CONFIRMATION_REQUIRED` 的确认或拒绝。
- `POST /openapi/v1/agents/conversations/{conversationId}/handoff/release`：人工结束后显式恢复 AI；不得通过再次发送普通消息隐式恢复。

每个端点均验证产品版本、服务账号、交互状态、过期时间与交互数据 Schema，并要求独立幂等键。交互状态机固定为 `PENDING -> ANSWERED | CANCELLED | EXPIRED`，终态不得逆转；`HUMAN_HANDOFF` 只允许由业务系统显式释放或关闭会话。

### 3.4 幂等性

- 每条外部客户消息生成唯一 `Idempotency-Key`。
- 重放相同请求只返回首次安全结果，不重复写入会话或重复调用工具。
- 网络超时后，业务系统必须用原幂等键重试，不能生成新键。
- 多渠道接入时，幂等键建议包含渠道、外部消息 ID 和消息方向。
- 幂等作用域固定为 `serviceAccountId + productProfileId + operation + Idempotency-Key`，不得只按应用空间或 Agent ID 去重。
- 平台保存规范化请求的 SHA-256 指纹（至少覆盖产品版本、会话、输入、业务 ID、上下文版本和操作）。同一作用域内键相同但指纹不同，返回 `IDEMPOTENCY_KEY_REUSED`，不返回第一次的结果。
- 文档须定义键的字符集、最大长度、缓存保留期和运行中锁租约；锁过期不能导致同一消息并发执行两次。

## 4. 客服系统接入设计

### 4.1 会话映射

平台 `conversationId` 是 Agent 上下文标识；业务系统必须维护外部会话映射，不能每条消息新建会话。

建议业务系统维护：

| 字段 | 说明 |
| --- | --- |
| `channel` | `web`、`app`、企业微信等渠道。 |
| `customer_id` | 业务系统客户 ID。 |
| `external_conversation_id` | 渠道侧会话 ID。 |
| `product_code` | 实际接入的 Agent 产品版本。 |
| `agent_conversation_id` | 平台返回的 `conversationId`。 |
| `status` | `AI_HANDLING`、`HUMAN_HANDLING`、`CLOSED`。 |
| `last_message_at` | 用于会话空闲回收和运营统计。 |

处理流程：

1. 客户消息到达业务系统。
2. 以 `channel + externalConversationId` 查询映射。
3. 首次调用不传 `conversationId`，保存平台返回的会话 ID。
4. 后续消息传入已保存的 `conversationId`，保持多轮上下文。
5. 会话处于 `HUMAN_HANDLING` 时停止向 Agent 投递消息。
6. 会话关闭或超时后按业务规则清理映射；新会话重新创建平台会话。

会话归属模型固定为“业务应用空间内、产品版本绑定的业务会话”，而不是服务账号私有会话。平台会话须保存创建服务账号，并允许同应用空间内已获该产品版本授权的服务账号续接；否则须拒绝。所有同步、标准异步和 Deep Agent 链路使用同一规则，不能一条按服务账号用户 ID 校验、另一条只按应用空间校验。

同一会话的客户消息必须按序执行。请求携带单调递增的 `messageSequence` 或 `expectedLastSequence`；平台以会话行锁/乐观锁保证一次只执行一轮，并在顺序冲突时返回可重试的 `CONVERSATION_SEQUENCE_CONFLICT`。幂等重放返回原结果而不占用新序号。

### 4.2 受控客户上下文

客服 Agent 常需查询订单、物流或售后，但不能将客户的完整资料拼入自然语言 `input`。

`context` 的设计原则：

- 只允许产品声明的键，例如 `customerId`、`channel`、`externalConversationId`。
- 平台校验名称、类型、长度和业务应用空间，不允许任意 JSON 透传。
- `context` 只写入服务端受控运行/会话元数据，默认不拼入模型消息。
- 工具执行时从受控上下文读取客户身份，并验证资源归属。
- 模型和工具结果只能获得完成任务所需的最小数据。
- 日志、审计、回调和诊断输出必须按敏感数据策略脱敏。

身份类键（例如 `customerId`、`accountId`）在会话创建后默认不可变。变更必须关闭并新建会话，或通过审计化迁移接口创建新的受控上下文版本；普通消息请求不得覆盖。每个键的声明还应包含来源、可见对象、保存期限、加密要求和是否可传给工具；白名单只限制键名，不等同于授权。

例如，订单工具接到模型提出的订单查询请求后，必须将请求限制为：

```text
applicationId = 当前服务账号的业务应用空间
customerId = 当前受控会话上下文的 customerId
```

模型生成的客户 ID、订单归属或越权参数不得作为授权依据。

### 4.3 人工转接

转人工是对话协议标准事件，不是自然语言约定。建议标准化：

```json
{
  "answer": "抱歉给您带来不便。我已为您转接人工客服。",
  "interactionStatus": "PENDING",
  "interactionType": "HUMAN_HANDOFF",
  "interactionData": {
    "reason": "REFUND_DISPUTE",
    "ticketId": "CS-20260827-1001"
  }
}
```

业务系统收到 `HUMAN_HANDOFF` 后应：

1. 创建或关联自身工单。
2. 将外部会话状态切换为 `HUMAN_HANDLING`。
3. 将对话摘要、可展示的知识引用和必要业务记录交给人工坐席。
4. 在人工处理期间停止调用 Agent，直至人工结束或显式释放会话。

Agent 通过已配置的 Prompt、Skill 或工单工具触发转人工；产品不重复配置转人工规则。

## 5. 运行时处理

### 5.1 平台执行顺序

1. 从服务账号令牌解析 `serviceAccountId` 和 `applicationId`。
2. 以 `applicationId + productCode` 查找状态为已发布/启动的产品版本。
3. 校验服务账号已授权该具体产品版本。
4. 校验产品类型为 `AGENT`，解析其不可变的完整可执行快照。
5. 校验或创建平台会话，确保其属于同一应用空间、绑定该产品版本并满足会话序号。
6. 校验、版本化保存允许的 `context`；身份类上下文不得被普通消息覆盖。
7. 将 `input` 写入本轮用户消息；加载会话历史、Agent Prompt、Skill、知识与工具上下文。
8. 执行 Agent；工具以受控上下文进行资源归属和权限校验。
9. 保存回答、结构化引用、交互状态/数据、运行记录、产品快照 ID、服务账号和审计数据。
10. 返回统一对话响应，剔除推理过程、工具参数、凭据和未脱敏敏感数据。

### 5.2 当前实现与目标差异

当前 `/openapi/v1/agents/chat` 已实现产品编码解析、启用状态校验、服务账号产品授权、会话续接、文本输入、文本回答和幂等处理。

当前请求 DTO 已预留 `context`，但同步问答调用链未将其保存为受控上下文，也未向工具提供可信客户身份。因此，在实现本方案前，业务系统不得将 `context` 视作已生效的订单或客户数据授权机制。

当前实现还只按应用空间校验异步运行的查询/取消权限，幂等键按应用空间与 Agent 作用域缓存，且同步/异步会话的调用者校验不一致。这些均不符合本文的目标契约，必须与产品版本绑定、服务账号归属和请求指纹一并修复后才能开放生产接入。

需要补齐：

- `context` 白名单及类型校验。
- 会话/运行受控上下文存储与应用空间隔离。
- 工具执行上下文注入及业务资源归属校验接口。
- `HUMAN_HANDOFF` 及其 `interactionData` 的稳定响应契约。
- 对话产品快照中 Agent 发布版本或完整可执行快照的解析。
- 交互提交/确认/释放端点及其状态机。
- 会话顺序控制、幂等请求指纹和产品版本级运行访问校验。

## 6. 版本与生命周期

### 6.1 产品版本

- `productId` 是稳定逻辑产品标识。
- 同一 `productId` 下的每条 `agent_product_profile` 记录代表一个具体版本。
- 服务账号的授权列表保存具体产品版本记录 ID，不自动跟随新版本。
- 新版本草稿从已发布版本复制，修改后发布为下一版本。
- 已发布版本不可编辑；需创建草稿版本调整。
- 停止版本拒绝新的外部调用，但不删除既有授权。

### 6.2 发布快照

发布快照至少应包含：

- 产品基础信息：逻辑产品 ID、版本号、编码、名称、应用空间和类型。
- 目标引用：Agent 定义 ID，以及可解析的 Agent 发布版本或可执行配置快照版本。
- 发布人、发布时间和来源草稿信息。
- 对外协议版本，例如 `conversation-api-v1`。

运行记录必须保存实际使用的产品版本快照标识。这样 Agent 定义之后被修改时，历史运行和进行中的会话仍可追溯其实际规则。

完整可执行快照至少包括：Agent 的模型与供应商版本、系统提示词、执行模式、工具/Skill 绑定及版本、知识库与检索策略版本、安全及交互策略、允许的上下文声明和协议版本。对外部引用保存不可变版本或内容哈希；被引用资源停用、删除或密钥轮换时，必须定义已运行会话继续、失败或迁移的策略与审计记录。

### 6.3 会话与版本一致性

新会话使用服务账号授权且已启用的产品版本。会话首次创建后，建议持久化 `productProfileId` 和产品版本号，后续消息继续使用这个版本。

同一客户会话中途不能静默切换到新 Prompt、知识库或工具策略。迁移到新版本只能通过明确策略完成：关闭旧会话、新建会话，或在业务系统向客户确认后迁移。

## 7. 数据模型演进

现有 `agent_product_profile` 字段中的 `inputSchema`、`outputSchema` 对 `WORKFLOW` 产品继续保留；对 `AGENT` 产品不作为运行时校验和对外契约。

建议新增或明确以下字段：

| 对象 | 字段/能力 | 用途 |
| --- | --- | --- |
| 产品版本 | `apiProtocolVersion` | 例如 `conversation-api-v1` 或 `workflow-api-v1`。 |
| 产品发布快照 | `agentDefinitionVersionId` 或 `agentSnapshot` | 固定发布时可执行的 Agent 配置。 |
| 会话 | `productProfileId`、`productVersionNo` | 固定会话使用的产品版本。 |
| 会话 | `serviceAccountId`、`messageSequence`、乐观锁版本 | 统一会话归属并保证消息有序。 |
| 运行 | `productProfileId`、`productVersionId`、`serviceAccountId`、`requestFingerprint` | 支持审计、访问校验和幂等冲突判断。 |
| 会话/运行上下文 | 加密或脱敏的 `trustedContext`、`contextVersion`、来源与密钥版本 | 保存允许的受控业务元数据。 |
| 产品或 Agent 接入策略 | `allowedContextKeys` | 声明可接收的上下文键、类型和敏感级别。 |
| 交互结果 | `interactionId`、`interactionData`、状态、过期时间、Schema 版本 | 承载并校验转人工、确认和补充信息。 |
| 幂等记录 | 作用域、键、请求指纹、状态、响应摘要、过期时间 | 使重放安全且可诊断。 |

不应新增产品级模型、Prompt、知识库、工具、Skill 的重复字段。

## 8. 管理台设计

产品管理页按产品类型显示字段：

### 8.1 Agent 产品

- 产品名称、编码、业务应用空间、说明。
- 绑定 Agent。
- 当前版本、草稿、发布历史、启停与删除。
- 服务账号按具体版本授权。
- 发布快照预览。
- 对话 API 文档和请求/响应示例。
- 可选：仅展示 Agent 侧声明的 `context` 键，供接入方了解；不在产品页编辑 Prompt、工具和知识库。

### 8.2 工作流产品

- 产品名称、编码、业务应用空间、说明。
- 绑定工作流。
- 输入 Schema、输出 Schema、回调与异步运行说明。
- 当前版本、草稿、发布历史、启停与删除。
- 服务账号按具体版本授权。

## 9. 错误码与安全要求

对话产品至少返回稳定的错误类别：

| HTTP 状态 | 错误码 | 含义 |
| --- | --- | --- |
| 401 | `SERVICE_ACCOUNT_INVALID` | 服务账号令牌缺失、过期或无效。 |
| 403 | `PRODUCT_NOT_ALLOWED` | 服务账号未授权产品或跨应用空间访问。 |
| 404 | `PRODUCT_NOT_FOUND` | 产品编码不存在或不可见。 |
| 409 | `IDEMPOTENCY_IN_PROGRESS` | 相同幂等键请求仍在处理中。 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 同一作用域的幂等键对应不同请求指纹。 |
| 409 | `CONVERSATION_SEQUENCE_CONFLICT` | 会话消息顺序冲突，调用方需读取最新序号后重试。 |
| 422 | `PRODUCT_TYPE_MISMATCH` | 将工作流产品调用到 Agent 接口，或反之。 |
| 422 | `CONVERSATION_NOT_ACCESSIBLE` | 会话不属于当前应用空间或绑定 Agent。 |
| 422 | `TRUSTED_CONTEXT_INVALID` | 传入了未允许或类型不正确的受控上下文字段。 |
| 422 | `INTERACTION_NOT_ACTIONABLE` | 交互不存在、非待处理、过期或动作与交互类型不匹配。 |
| 429 | `RATE_LIMITED` | 业务应用空间或服务账号超出额度。 |
| 500/503 | `AGENT_RUN_FAILED` | 安全包装后的运行失败，不返回内部异常。 |

必须满足：

- 业务系统调用产品，终端客户不直接持有平台服务账号。
- 对话、运行、工具调用、引用和审计均关联 `applicationId`、产品版本、会话 ID、运行 ID 与 `traceId`。
- 不返回模型推理过程、原始工具请求/响应、内部 Prompt、密钥或未脱敏个人信息。
- 高风险写工具必须由工具服务端再次授权；模型决定调用工具不等于获得业务写权限。
- `context`、日志、回调和运行快照均纳入数据分级、加密和脱敏策略。

## 10. 实施计划

### 阶段 1：固化产品边界

1. 在产品类型说明和管理台中明确：`AGENT` 使用统一对话协议，`WORKFLOW` 使用 Schema 协议。
2. Agent 产品页面隐藏或只读展示 `inputSchema`、`outputSchema`，不作为 Agent 配置项。
3. 能力发现接口按产品类型返回对应协议说明；Agent 返回 `conversation-api-v1`，工作流返回冻结 Schema。
4. 移除 OpenAPI 文档中 Agent 必须具备独立输入输出 Schema 的表述。
5. 发布 `conversation-api-v1` 的请求、成功响应、错误响应、引用和交互 JSON Schema，并定义产品编码/版本选择规则。

### 阶段 2：版本冻结与会话绑定

1. 发布时固定 Agent 的可执行版本或完整配置快照。
2. 在 Agent 会话、Agent 运行上记录产品版本标识。
3. 后续消息强制沿用会话创建时的产品版本。
4. 增加新建会话、旧会话关闭和显式迁移的版本策略。
5. 将产品版本、快照 ID、创建服务账号和会话序号写入会话/运行；查询、取消和续接按该边界鉴权。

### 阶段 3：可信上下文与业务工具

1. 为 Agent 或产品引入只读的 `allowedContextKeys` 声明。
2. 校验、加密/脱敏并持久化 `trustedContext`。
3. 在工具执行上下文中注入可信 `applicationId` 和客户身份。
4. 为订单、工单、退款等工具增加资源归属校验和最小数据返回规则。
5. 固定身份类上下文不可变规则、上下文版本迁移流程、数据保留和密钥轮换策略。

### 阶段 4：客服交互事件

1. 统一 `HUMAN_HANDOFF`、`USER_INPUT_REQUIRED`、`USER_CONFIRMATION_REQUIRED` 等交互类型。
2. 新增经校验的 `interactionData`，不依赖解析回答文本。
3. 提供客服业务系统的会话映射、转人工和恢复 AI 处理接入示例。
4. 实现交互提交、确认和人工释放端点，并覆盖重复提交、过期、取消与越权测试。

### 阶段 5：验收

1. 同一服务账号只能调用被授权的具体 Agent 产品版本。
2. 同一产品调用可维持多轮上下文，且跨应用、跨 Agent 会话被拒绝。
3. Agent 新版本发布不影响存量会话；显式迁移后才生效。
4. 客户上下文不能直接泄漏进 Prompt、日志或跨客户工具查询。
5. 订单工具不能因模型伪造客户 ID 查询其他客户数据。
6. 转人工仅依赖标准交互事件触发，业务系统不解析自然语言。
7. 产品停止后拒绝新会话；历史运行、授权和审计仍可查询。
8. 不同产品版本、服务账号或请求正文复用相同幂等键时，不会相互返回错误结果；同一请求重放不重复执行工具。
9. 同一会话的乱序并发消息被拒绝或串行化，且跨服务账号的续接严格符合已定义的会话归属规则。
10. 运行查询、取消和交互恢复不能越过具体产品版本授权。

## 11. 与现有文档的关系

本文覆盖对话型 `AGENT` 产品的正式设计。工作流异步调用、变量 Schema 校验及回调机制继续遵循现有 OpenAPI 文档和工作流运行时实现。

`16-Agent中台业务赋能规划` 中关于所有 Agent 产品均绑定输入/输出 Schema、同步 Agent 必须返回结构化结果的历史规划，与本文的对话 Agent 协议存在差异。后续实施时应以本文为准，并修订旧规划与 OpenAPI 说明中的相关表述。
