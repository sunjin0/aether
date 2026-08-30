# 企业工作流 2.0 完整修改方案

> 状态：实施中。P0 已完成规则、转换、HTTP、邮件通知、外部调用记录、审计轨迹、人工确认/重试恢复和工具节点名称迁移；P1 已完成延时、事件等待、服务账号审批和固定版本子流程，并补齐工具节点审批策略、等待超时兜底与子流程交互透传；P2 已接入确定性并行分支、汇聚状态和变量快照，并行分支异步调度仍在增强。
>
> 目标：在既有 Agent、MCP、人工节点和企业触发/审计能力上，补齐确定性业务编排、外部副作用保护、子流程和并行汇聚能力。

## 1. 定位与原则

工作流用于可控、可复现、可审计的业务过程；Agent 用于非确定性的理解、总结和决策辅助。两者组合，但不能把所有确定性工作交给 Agent。

节点展示名称采用“工具节点”，而非“MCP 节点”。MCP 是当前工具接入协议；产品层不应把协议名称暴露为业务建模概念。现有画布 `type: "mcp"` 和接口保持兼容，后续新定义可逐步采用 `type: "tool"`，运行时将两者归一处理。

原则：

- HTTP、规则、数据转换和通知使用确定性节点，不使用 Prompt 代替业务逻辑。
- 任何有外部副作用的调用都要有幂等键、调用意图、结果记录和人工恢复路径。
- 子流程绑定已发布版本，不能随草稿变化。
- 服务账号是流程启动、人工回答和审批操作的实际主体。
- 业务端只读取 `outputSchema` 声明的输出；内部变量和审计记录仅向管理端开放。

```text
触发层：业务 API / Webhook / Cron / 手工启动
编排层：规则 / 并行 / 汇聚 / 子流程 / 等待 / 审批
执行层：Agent / 工具 / HTTP / 转换 / 通知
治理层：版本 / 权限 / 幂等 / 审计 / SLA / 回调 / 监控
```

## 2. 现有能力与边界

当前已实现：顺序图、条件分支、受限循环、`agent`、工具节点（兼容 `mcp`）、`human`、`approval`、`rule`、`transform`、`http`、邮件通知、`wait_event`、`delay`、Webhook、Cron、业务 API 启动、SSE、版本快照、输入输出 Schema、回调、SLA、审计和服务账号权限。

当前限制：并行分支已支持确定性节点的 token 记录和汇聚状态，交互/等待节点仍禁止作为并行分支；尚未增加真正的分支级异步调度。等待事件已提供实例定向与按事件类型/关联键的服务账号入口，通用入口按 `eventId` 去重。子流程已支持固定版本、输入输出映射、父子实例关联、终态回传和父级截止时间向下传播；父实例详情还可穿透显示最深层子流程的人工交互或审批，指定审批服务账号时不可由父流程发起人代答。变量快照已在节点完成后脱敏保存并提供管理端查询。

## 3. 节点体系

保留 `start`、`end`、`agent`、工具节点、`human`。工具节点当前兼容 `type: "mcp"`，后续规范类型为 `tool`。新增节点如下：

| 节点 | 类型 | 配置重点 |
| --- | --- | --- |
| 条件规则 | `rule` | 条件组、命中分支、默认分支、规则版本 |
| 数据转换 | `transform` | JSONPath、字段映射、模板、默认值、类型校验 |
| 工具 | `tool` | 工具资源、参数映射、确认策略、超时、幂等键；兼容存量 `mcp` |
| HTTP/API | `http` | URL、认证引用、请求/响应映射、超时、幂等键 |
| 通知 | `notification` | 渠道、模板、收件人映射、失败策略 |
| 并行分叉 | `parallel` | 分支列表、并发上限、`maxBranches`、`branchTimeoutMillis` |
| 汇聚 | `join` | 全部成功、任一成功、允许部分失败、超时策略 |
| 子流程 | `subflow` | 工作流和固定版本、输入输出映射、超时传播 |
| 延时 | `delay` | 固定时长或截至时间 |
| 等待事件 | `wait_event` | 事件类型、关联键表达式、超时分支 |
| 审批 | `approval` | 服务账号、时限、自动决策、会签/或签、升级 |

`human` 节点继续兼容。新流程使用 `approval` 承载审批语义，`human` 用于简单录入。

工具节点的 `toolApprovalPolicy` 取值为 `ask`、`risky` 或 `never`：默认 `ask`；`risky` 仅在调用被风险分析器判定为高风险时等待确认；`never` 自动放行。工具或参数无法解析时按高风险处理。人工、审批和工具确认节点会继承实例截止时间；未设置时依次采用节点 `timeoutMillis` 和服务端全局兜底时间，避免实例永久等待。

## 4. 运行时改造

### 4.1 并行与汇聚

将单一 `currentNodeId` 扩展为可追踪的节点执行令牌：

```text
parallel
  ├─ 分支 A ─┐
  ├─ 分支 B ─┼─ join ─ 后续节点
  └─ 分支 C ─┘
```

- `parallel` 为每条分支创建独立 token。
- 分支可独立失败、重试和审计。
- `join` 依据策略决定继续、失败或走降级边。
- token 唯一键防止同一分支重复执行。

### 4.2 外部副作用保护

适用于 `tool`（包括存量 `mcp`）、`http`、通知和子流程启动：

```text
PENDING → INVOCATION_RECORDED → RUNNING → COMPLETED / FAILED / UNKNOWN
```

- 外部调用前通过独立事务保存调用意图和幂等键。
- 请求携带 `X-Idempotency-Key` 或业务系统约定字段。
- 调用成功后保存响应摘要和映射结果。
- 超时、崩溃、租约过期进入 `UNKNOWN`，不自动重放。
- 管理端提供查询外部结果、确认成功和显式重试。

### 4.3 子流程

子流程启动子实例，并记录父实例、父 token、目标工作流、固定版本和映射关系。子实例终态后，把其输出契约字段映射回父变量。

子流程节点可配置 `timeoutMillis`；子实例截止时间取该节点超时时间与父实例 `deadlineAt` 的较早值。

发布校验必须拒绝：未发布子流程、递归引用、循环引用和不合法映射。

### 4.4 事件等待

`wait_event` 进入 `WAITING_EVENT`。事件入口按事件类型和关联键唤醒匹配 token：

```http
POST /api/business/workflow-events/{eventType}
```

事件节点必须配置关联表达式、去重键和超时分支。

并行节点可配置 `maxBranches`（1–50）和 `branchTimeoutMillis`；超限时进入现有失败/汇聚策略，不会自动重放外部副作用。

## 5. 数据库迁移

新增 Flyway 迁移，不修改已应用迁移：

- `agent_workflow_node_token`
- `agent_workflow_join_state`
- `agent_workflow_external_invocation`
- `agent_workflow_subflow_link`
- `agent_workflow_waiting_event`
- `agent_workflow_approval_task`
- `agent_workflow_approval_action`
- `agent_workflow_variable_snapshot`

关键约束：

- `(instance_id, node_id, token_key)` 唯一。
- `(node_token_id, idempotency_key)` 唯一。
- 审批任务只允许单向终态迁移。
- 审计事件只新增，不提供业务修改或删除接口。

## 6. 接口设计

管理端：

```text
GET  /api/agent/workflow/node-types
POST /api/agent/workflow/{id}/dependencies/validate
GET  /api/agent/workflow/instances/{id}/timeline
GET  /api/agent/workflow/instances/{id}/tokens
POST /api/agent/workflow/instances/{id}/nodes/{nodeId}/retry
POST /api/agent/workflow/instances/{id}/external-invocations/{id}/confirm
POST /api/agent/workflow/instances/{id}/external-invocations/{id}/retry
GET  /api/agent/workflow/approval-tasks
POST /api/agent/workflow/approval-tasks/{id}/complete
```

业务端：

```text
POST /api/business/workflows/{workflowId}/instances
GET  /api/business/workflows/instances/{instanceId}/result
GET  /api/business/workflows/instances/{instanceId}/timeline
POST /api/business/workflow-events/{eventType}
```

## 7. 画布和发布校验

前端画布按节点类型提供配置表单。密钥仅保存引用 ID，不显示明文。

发布时校验：

- 所有路径最终可到达 `end`。
- 并行必须有对应汇聚。
- 子流程版本已发布且没有依赖环。
- 变量引用在图的数据流中可达。
- 外部副作用节点具备幂等键，或显式声明只读。
- 审批节点已配置服务账号和超时策略。

## 8. 实施计划

### P0：确定性业务节点和外部调用安全

1. 实现 `transform`、`rule`、`http`、`notification`。
2. 实现外部调用意图、幂等键、`UNKNOWN` 和人工确认恢复。已完成 HTTP 与通知节点，工具和子流程待接入同一调用记录模型。
3. 增加实例时间线、调用记录、节点 token 查询。
4. 覆盖超时、进程重启、租约过期和幂等的集成测试。

### P1：复用与异步闭环

1. 实现 `subflow`。已支持固定版本启动、父子实例关联、输入输出映射和循环引用校验。
2. 实现 `delay` 与 `wait_event`。已支持实例定向事件和 `POST /api/business/workflow-events/{eventType}` 通用事件入口。
3. 实现 `approval`：时限、升级、会签和或签。

### P2：复杂编排

1. 实现 `parallel` 与 `join`。已支持确定性分支的 token、汇聚策略和状态持久化；分支级异步调度仍待增强。
2. 增加分支级配额、超时和降级。
3. 实现变量快照、指定节点回放和调试视图。

## 9. 验收标准

- 外部调用在异常、租约过期和重启后不会被静默重复执行。
- 单个实例可追溯定义版本、输入、执行路径、变量变化、服务账号操作、外部调用和回调结果。
- 子流程绑定发布版本，后续草稿修改不影响运行实例。
- 并行分支能独立完成、失败和重试，汇聚严格按策略执行。
- 业务端只返回 `outputSchema` 字段。

## 10. 非目标

- 不引入任意代码执行节点。
- 不以 LangGraph 替换当前企业工作流产品。
- 不取消版本、权限、服务账号、审计和幂等约束来换取“灵活性”。
