# Agent 调用工作流能力方案

> 文档状态：设计评审稿 + P0/P1/P2 基础实现说明。  
> 设计日期：2026-09-14。  
> 范围：让 Agent 将受控的已发布工作流作为业务能力调用；不改变工作流作为确定性编排引擎的定位。

当前代码已落地可运行闭环：管理端可创建、更新、启停 Capability；Agent 工具目录按 Agent 和应用空间最多暴露一套固定工作流工具
`workflow_start`、`workflow_observe`、`workflow_stop`、`workflow_provide_input`、`workflow_resolve_mcp_approval`、
`workflow_signal_event`、`workflow_retry`。工作流数量不再线性增加模型工具数量；启动动作通过 `capabilityCode` 选择能力，后续动作通过 `invocationId` 选择实例。
领域服务实现已发布版本校验、启动幂等、观察脱敏、归属校验、停止、受控 Agent 输入、业务事件和失败节点重试。
工作流实例状态版本由数据库触发器统一递增，命令表记录受控动作，Outbox 负责状态变化的持久化通知与重试投递，并将事件写入已有 Agent Task 事件表。
Outbox 是可靠交付边界：它不绕过 Agent Runtime 私自启动新的模型运行；运行时按 `agentTaskId`/`agentRunId` 消费事件，判断是否需要继续同一 Agent Run。

实现入口映射：

| 层次 | 代码入口 |
| --- | --- |
| 数据库 | `api/src/main/resources/db/migration/postgresql/V213__agent_workflow_capability_invocation.sql` |
| 命令与 Outbox | `api/src/main/resources/db/migration/postgresql/V214__agent_workflow_invocation_commands_outbox.sql` |
| 能力管理 | `admin/.../AgentWorkflowCapabilityController` |
| 工具目录 | `biz/.../AgentToolCatalog` 固定生成 7 个工作流工具；`capabilityCode` 和 `invocationId` 负责路由 |
| 领域服务 | `biz/.../AgentWorkflowInvocationServiceImpl` |
| Agent 执行器 | `biz/.../WorkflowToolExecutor` |
| 输出脱敏 | 复用 `WorkflowOutputResolver`，仅返回发布版本 outputSchema 声明字段 |
| 验证 | `WorkflowToolExecutorTest`、`WorkflowOutputResolverTest`；多模块 compile 已通过 |

## 1. 背景、目标与结论

平台现有工作流已支持版本快照、异步执行、业务启动、人工交互、事件等待、节点重试、终止、审计轨迹和 SSE 观察。当前 `agent` 节点的能力是“工作流调用 Agent”；本方案解决相反方向：**Agent 调用工作流**。

目标是让 Agent 能够对授权业务能力执行以下操作：

1. 启动工作流实例。
2. 停止自己有权控制的实例。
3. 观察实例状态、进度、可执行的下一动作及最终输出。
4. 在工作流明确许可的范围内补充结构化信息、触发事件或重试节点。
5. 在工作流完成或进入可处理等待态后，以可靠方式继续 Agent 任务。

本方案不把每一个已发布工作流自动暴露给所有 Agent，也不允许 Agent 任意修改流程状态、模拟人工审批或直接访问工作流控制器。Agent 通过“工作流能力（Workflow Capability）”调用；Capability 是对某个冻结工作流版本、输入输出契约、权限范围和风险策略的受控封装。

```text
用户 / 业务请求
      ↓
Agent Run ──工具调用──> WorkflowToolExecutor
                              ↓
                    AgentWorkflowInvocationService
                              ↓
              Workflow Capability（版本、权限、策略）
                              ↓
                  AgentWorkflowExecutionService
                              ↓
        工作流实例、节点、后台任务、审计与最终输出
```

## 2. 现有基础与差距

### 2.1 可复用能力

| 现有能力 | 当前实现 | 在本方案中的用途 |
| --- | --- | --- |
| 工作流发布版本 | `agent_workflow_version` | 固定 Agent 实际调用的流程定义 |
| 输入/输出 Schema | 工作流定义及版本快照 | 校验工具输入，限制对 Agent 的结果暴露 |
| 实例异步执行 | `AgentWorkflowExecutionService` 和持久化执行任务 | 启动后立即返回实例引用，不阻塞 Agent 工具调用 |
| 实例操作 | `answer`、`retry`、`retryNode`、`terminate`、`signalEvent` | 实现受控介入、继续及停止 |
| 审计与节点记录 | `agent_workflow_audit_event`、节点实例、变量快照 | 观察、取证、恢复和 Dashboard 展示 |
| SSE | `WorkflowSseHub` | 在线 UI 实时刷新，非可靠消息通道 |
| Agent 工具调用记录 | `AgentToolCallLog` | 保留模型工具调用审计并关联业务调用 |

### 2.2 必须补齐的能力

- 工作流的“可由 Agent 调用”发布策略与版本冻结。
- Agent Run、工具调用、委托身份和工作流实例之间的强关联。
- 按动作和状态控制的 Agent 调用领域服务。
- 对 Agent 的脱敏观察 DTO 及稳定的 `nextAction` 协议。
- 可靠的工作流状态变化通知与 Agent 任务唤醒机制。
- Agent 与人工职责分离的审批和干预策略。

## 3. 核心概念与职责边界

### 3.1 Workflow Capability

Workflow Capability 是 Agent 工具目录中可见的一个业务能力，绑定一个工作流的特定已发布版本。它不是工作流草稿，也不是普通管理端 REST 权限。能力配置与 Agent 绑定分离：工作流能力页只维护能力本身，具体安装关系写入 `agent_definition_workflow_capability_binding`，从智能体配置操作栏完成绑定。

```json
{
  "capabilityCode": "ticket_review",
  "workflowId": "workflow-001",
  "workflowVersionId": "workflow-version-12",
  "enabled": true,
  "allowedAgentIds": ["ticket-assistant"],
  "allowedActions": ["START", "OBSERVE", "STOP", "PROVIDE_AGENT_INPUT", "RESOLVE_MCP_APPROVAL"],
  "riskLevel": "MEDIUM",
  "agentWritableVariables": ["suggestedAssignee"],
  "allowedEventTypes": ["ticket.material.completed"]
}
```

Capability 不再对应一套独立工具。所有能力共用固定工具协议：

```text
workflow_start(capabilityCode, input, operationKey?)
workflow_observe(invocationId?)
workflow_stop(invocationId?, reason?)
workflow_provide_input(invocationId?, input)
workflow_resolve_mcp_approval(invocationId?, decision)
workflow_signal_event(invocationId?, eventType, eventId, ...)
workflow_retry(invocationId?, nodeId, reason?)
```

`workflow_start` 只接受当前 Agent 已绑定且已发布的 `capabilityCode`，服务端解析到 Capability ID 和冻结版本。不要提供一个可自由填入 `workflowId` 的万能工具，也不要把数百个工作流重复展开到模型工具上下文。

**目标解析（先查后改）**：`invocationId` 一律可选。不传时由服务端按当前会话把目标定下来 —— 会话内恰好一条未终态的调用就直接用它，一条都没有或多于一条则返回结构化结果（后者附候选列表），不替模型猜。`PROVIDE_AGENT_INPUT`、`RESOLVE_MCP_APPROVAL`、`SIGNAL_EVENT`、`RETRY_NODE` 这类状态绑定的动作会先用 `nextAction` 收窄候选，只有一条在等这个动作时同样直接采用。显式传 `invocationId` 仍然有效，用于接管别的会话里启动的调用。

**状态版本不再由模型回传**：`expectedStateVersion` 已从全部工具参数中去掉，服务端在执行时读当前值并照旧做乐观锁校验。这样「读」发生在服务端一次事务内，模型不必先 `OBSERVE` 再回传一个它看到的版本 —— 那次往返既费 token，又制造了「模型照着旧快照操作」的机会。

### 3.2 身份模型

一次调用至少包含三种身份：

| 身份 | 含义 | 用途 |
| --- | --- | --- |
| 最终授权主体 `principal` | 用户、服务账号或系统任务 | 判断可访问的业务空间和调用额度 |
| Agent 定义 | 做出工具调用决策的 Agent | Capability 白名单、模型审计和风险策略 |
| Agent Run / Tool Call | 某次具体执行与模型函数调用 | 幂等、诊断和任务恢复 |

工作流实例既有 `userId` 继续表达业务归属，不能用它替代 Agent 来源。Agent 来源、委托范围和工具调用证据写入独立 invocation 记录及审计事件。

### 3.3 禁止边界

- Agent 不通过内部 HTTP 回环调用 `/api/agent/workflow/**`，而是调用领域服务。
- Agent 不可设置 `currentNodeId`、直接写节点输出、覆盖完整变量 JSON 或删除实例。
- Agent 不可自动回答 `HUMAN_REQUIRED` 审批节点。
- Agent 不可确认结果未知的有副作用外部调用，除非策略要求的人类审批已经完成。
- SSE 不用于可靠的 Agent 唤醒；它只用于在线界面刷新。

## 4. 业务能力与动作协议

Agent 只使用以下明确语义的动作，避免模糊的“继续”或万能“介入”接口。

| 动作 | 用途 | 允许条件 |
| --- | --- | --- |
| `START` | 创建新实例 | Capability 允许启动、输入通过 Schema、调用额度未超限 |
| `OBSERVE` | 读取脱敏操作快照 | 调用者与实例关联且具有观察权限 |
| `STOP` | 请求终止实例 | Capability 允许停止，调用者对实例有控制权 |
| `PROVIDE_AGENT_INPUT` | 向 Agent 专用交互节点提交结构化输入 | 节点声明 `AGENT_INPUT_ALLOWED` |
| `RESOLVE_MCP_APPROVAL` | 回复 MCP 授权节点并决定是否执行工具 | Capability 显式允许；节点状态为 `mcp_tool_approval`；决定值为 `once`、`allow_10m` 或 `reject` |
| `SIGNAL_EVENT` | 提交已声明业务事件 | Capability、事件类型与关联键均匹配 |
| `RETRY_NODE` | 重试当前失败节点 | 节点与策略显式允许，且不存在未知外部副作用 |

`CONTINUE` 仅作为 Agent 产品层的交互文案，不作为领域命令。`OBSERVE` 用来观察进度、读取结果和确认下一步，不再作为提交动作前的必经步骤 —— 动作工具自己会解析目标并读取状态版本。

### 4.1 启动

```json
{
  "capabilityCode": "ticket_review",
  "operationKey": "task-123:plan-step-4:ticket-review",
  "input": {
    "ticketId": "T-20260914-001",
    "priority": "HIGH",
    "reason": "客户投诉升级"
  }
}
```

服务端依次完成：解析绑定的 Capability 和冻结版本、校验委托主体与 Agent、校验输入 JSON Schema、创建或复用 invocation、创建工作流实例、将执行任务入队，并写入审计记录。返回时不等待工作流完成。

```json
{
  "invocationId": "inv-001",
  "instanceId": "instance-001",
  "workflowVersion": 12,
  "status": "RUNNING",
  "nextAction": {"type": "OBSERVE"},
  "resultCode": "WORKFLOW_INVOCATION_ACCEPTED"
}
```

### 4.2 观察

```json
{
  "instanceId": "instance-001",
  "detailLevel": "SUMMARY",
  "afterEventId": "event-002"
}
```

返回给模型的数据必须是专用 DTO，不能将工作流实体、原始变量、第三方 HTTP 响应或审计载荷直接序列化给模型。

```json
{
  "instanceId": "instance-001",
  "status": "WAITING_AGENT_INPUT",
  "stateVersion": 7,
  "currentNode": {
    "nodeId": "assignmentSuggestion",
    "nodeType": "interaction",
    "name": "分派建议"
  },
  "nextAction": {
    "type": "PROVIDE_AGENT_INPUT",
    "schema": {
      "suggestedAssignee": {"type": "string", "required": true}
    }
  },
  "trustedFields": {
    "resultCode": "WORKFLOW_WAITING_AGENT_INPUT"
  },
  "untrustedBusinessData": {
    "ticketSummary": "来自业务系统的内容"
  },
  "recentEvents": []
}
```

其中用户提交、第三方系统返回及文档文本均属于不可信业务数据；Agent 运行时不能把它们提升为系统指令、权限依据或可执行工具参数。MCP 授权节点另有明确的 `RESOLVE_MCP_APPROVAL` 动作：只有用户在聊天中明确表达授权意图、且 Capability 已显式开放该动作时，Agent 才能提交决定；工作流仍复用原有 MCP 幂等、异步执行和未知结果保护。

### 4.3 停止

停止语义是“请求终止后续执行”，不是删除，也不承诺撤销已经发生的外部副作用。

```text
RUNNING / WAITING_USER / WAITING_EVENT / WAITING_SUBFLOW
                  → TERMINATED
```

第一期直接复用已有 `terminate` 语义，不新建 `PAUSED` 状态。暂停会影响后台任务、延时/事件恢复、子流程收敛和超时调度，应作为独立后续课题。

### 4.4 受控输入与人工审批

交互节点应区分三种策略：

| 策略 | Agent 权限 |
| --- | --- |
| `HUMAN_REQUIRED` | Agent 只能观察和提出建议，不能提交答案 |
| `AGENT_INPUT_ALLOWED` | Agent 可按节点 Schema 提交结构化输入 |
| `AGENT_PROPOSAL` | Agent 提交建议，实例仍等待人工确认 |

对于付款、权限变更、采购、删除、外部写操作确认等高风险行为，默认仍要求人工确认。`RESOLVE_MCP_APPROVAL` 不是万能审批权限：它只处理当前调用者拥有的 MCP 授权节点，并且由用户聊天中的明确授权触发；Capability 未开放该动作、节点不是 `mcp_tool_approval`、状态版本已变化或外部结果未知时，服务端拒绝操作。不得将普通 Agent 的 `PROVIDE_AGENT_INPUT` 映射为人工审批通过。

### 4.5 失败恢复

`RETRY_NODE` 仅可用于当前失败节点，且由工作流版本和 Capability 同时授权。外部副作用结果未知时，应维持现有“人工核对/确认后重试”模型，Agent 不得自动重放。

## 5. 状态机、并发与幂等

### 5.1 状态分层

工作流实例继续使用既有状态机；本方案不替代或复制工作流状态。

```text
工作流实例：RUNNING → WAITING_* → RUNNING → COMPLETED / FAILED / TERMINATED / TIMED_OUT
调用记录：ACCEPTED → RUNNING → WAITING_ACTION → COMPLETED / FAILED / TERMINATED
```

`WAITING_ACTION` 是调用记录面向 Agent 的投影，不意味着工作流实例新增同名状态。它由 `WAITING_AGENT_INPUT` 等实际节点状态推导。

### 5.2 乐观并发控制

改变实例的 Agent 动作都要过 `expectedStateVersion`。实例每发生一次状态或可操作性变化递增版本号；版本不匹配时拒绝操作，返回稳定码：

```text
WORKFLOW_INSTANCE_STATE_CHANGED
```

**这个版本号由服务端在执行时读取，不是模型传来的参数**：工具层在调用领域服务前先读当前值，领域服务在持有实例行锁的事务里再比对一次。竞态窗口因此从「模型上一次观察到现在」缩到一次事务，而模型那一侧不必再 `OBSERVE` 一趟。领域服务仍保留显式传入版本的入口，供非模型的内部调用方使用。

### 5.3 幂等键

模型工具调用 ID 在模型重试、任务恢复时不一定稳定，不能作为唯一业务幂等键。启动使用两层关联：

```text
业务幂等：capabilityId + principalId + operationKey
运行证据：agentRunId + toolCallId
```

`operationKey` 由 Agent 编排器在同一任务与计划步骤中稳定产生，例如 `taskId:planStepId:capabilityCode`。数据库对前者建立唯一约束；后者仅作追踪和诊断。

## 6. 数据模型与迁移

所有变更必须以新的 Flyway 迁移完成；不得修改已应用迁移。建议首个迁移为 `V176__add_agent_workflow_capability_and_invocation.sql`，实际版本号以当时迁移目录最高版本后的可用编号为准。

### 6.1 Capability

```text
agent_workflow_capability
- id
- application_id
- workflow_id
- workflow_version_id
- capability_code
- display_name
- enabled
- policy_json
- input_schema
- output_schema
- created_at / updated_at / sort_num / deleted / state
```

关键约束：

- `(application_id, capability_code)` 在未删除记录中唯一。
- `workflow_version_id` 必须是已发布版本；更新 Capability 要创建新版本或显式切换绑定，不能悄悄漂移。
- `policy_json` 中的 Agent、动作、可写变量、事件类型和风险策略在保存和启用时校验。

### 6.2 Invocation

```text
agent_workflow_invocation
- id
- capability_id
- workflow_instance_id
- agent_definition_id
- agent_run_id
- agent_task_id
- principal_type
- principal_id
- service_account_id
- tool_call_id
- operation_key
- status
- input_snapshot
- output_snapshot
- created_at / started_at / completed_at / updated_at / deleted / state
```

关键索引与唯一约束：

```text
UNIQUE(capability_id, principal_id, operation_key) WHERE deleted = FALSE
UNIQUE(workflow_instance_id) WHERE deleted = FALSE
INDEX(agent_run_id, created_at DESC)
INDEX(principal_id, created_at DESC)
```

第二期新增命令表，支持审批、异步确认、失败重放和操作审计：

```text
agent_workflow_invocation_command
- id, invocation_id, command_type, command_payload
- requested_by_type, requested_by_id
- expected_state_version
- status, result_code, result_summary
- requested_at, applied_at
```

第一期的启动、停止和受限输入可使用 invocation 审计事件；一旦接入异步审批或可靠唤醒，命令表成为必需。

## 7. 服务、接口与返回契约

新增内部领域服务，避免工具执行器直接分散调用工作流实现：

```java
public interface AgentWorkflowInvocationService {
    AgentWorkflowInvocationResult start(AgentWorkflowInvocationStartCommand command);
    AgentWorkflowObservation observe(String invocationId, AgentWorkflowInvocationPrincipal principal);
    AgentWorkflowInvocationResult provideAgentInput(AgentWorkflowAgentInputCommand command);
    AgentWorkflowInvocationResult signalEvent(AgentWorkflowSignalEventCommand command);
    AgentWorkflowInvocationResult retryNode(AgentWorkflowRetryNodeCommand command);
    AgentWorkflowInvocationResult stop(AgentWorkflowStopCommand command);
}
```

`WorkflowToolExecutor` 负责从 Agent 工具上下文构造受限 principal，并调用上述服务。领域服务负责校验和事务边界，再复用现有 `AgentWorkflowExecutionService` 的 `start`、`answer`、`signalEvent`、`retryNode`、`terminate` 等能力。

不建议第一期新增面向浏览器的通用“Agent 调用工作流”公开 REST 接口。若需要开放平台接入，应使用独立的服务账号认证、能力白名单和请求签名，不复用管理端 Controller 的用户身份语义。

返回的 `status`、`nextAction.type`、`resultCode` 和错误码必须是稳定机器码。任何面向用户的提示语通过 `I18nUtils.getMessage(...)` 在服务端本地化，不能在新 Controller 或 Service 中写硬编码自然语言。

## 8. 权限、风险和数据保护

一次调用的最终权限是下列集合的交集：

```text
Capability 允许的 Agent
∩ Capability 允许的动作
∩ principal 的应用空间与工作流范围
∩ 服务账号白名单与额度
∩ 当前实例归属和状态
∩ 风险/审批策略
```

建议新增资源权限：

```text
/workflow/capability/read
/workflow/capability/write
/workflow/invocation/observe
/workflow/invocation/start
/workflow/invocation/intervene
/workflow/invocation/stop
/workflow/invocation/operate-any
```

`operate-any` 仅授予平台运维管理员。正常 Agent 只能观察和控制自身、同一委托主体发起的 invocation。

安全要求：

- 输入快照、输出快照和审计数据按现有敏感字段策略递归脱敏。
- 凭证、授权头、Webhook 密钥、模型原始响应和工具原始响应不能进入 Agent 可见 DTO。
- Capability 输出必须只映射工作流 `outputSchema` 声明的字段。
- 模型工具参数必须做 Schema 校验、长度限制和允许字段过滤。
- 所有启动、停止、输入、重试和审批操作写入不可变审计事件。

## 9. 异步完成与可靠唤醒

Agent 不应以长连接或高频轮询等待流程完成。第一期可让 Agent 在需要结果时主动 `OBSERVE`；正式闭环应增加可靠唤醒：

```text
工作流终态或等待态变化
      ↓ 写入持久化审计/Outbox
可靠投递器读取 invocation 关联
      ↓
将 Agent Task 标记为可恢复或投递恢复任务
      ↓
Agent Run 重新获得上下文
      ↓
OBSERVE 后处理 nextAction 或最终 outputSchema
```

唤醒逻辑必须可重试、可去重、可对账。服务重启后应从未确认的 outbox/审计事件恢复；不能以 `WorkflowSseHub` 的内存广播作为事实来源。

## 10. 实施阶段与验收

### P0：受控启动、观察和停止（当前已实现）

范围：Capability、冻结版本、`START`、`OBSERVE`、`STOP`、invocation 关联、幂等、脱敏和审计。

验收：

- 同一 `operationKey` 重放只生成一个工作流实例。
- 未绑定 Agent 或越过应用空间的请求被拒绝。
- 观察结果不包含敏感变量和内部节点载荷。
- 版本切换不影响已创建 invocation。

### P1：Agent 专用输入与状态并发控制（当前已实现）

范围：`PROVIDE_AGENT_INPUT`、状态版本校验、`AGENT_INPUT_ALLOWED` 节点策略。

验收：

- 重复停止是幂等的；终态实例不会再次推进。
- Agent 无法提交 `HUMAN_REQUIRED` 节点答案。
- 旧快照提交输入时，服务端返回 `WORKFLOW_INSTANCE_STATE_CHANGED`。

### P2：受控恢复与可靠唤醒（基础设施已实现）

范围：`SIGNAL_EVENT`、`RETRY_NODE`、命令表、审批衔接、事件 outbox、Agent Task 恢复。

验收：

- 有未知外部副作用的节点不会被 Agent 自动重试。
- 工作流完成、失败、终止、超时和等待事件可在服务重启后从 Outbox 恢复并准确投递一次 Agent Task 事件；Agent Runtime 的具体 resume 由事件消费端按运行状态执行。
- 命令和唤醒重复投递不造成重复外部副作用。

### 非目标

- 不在本方案中引入工作流暂停状态。
- 不让 Agent 自动替代人工审批或批准高风险操作。
- 不暴露任意工作流、任意实例或任意变量给模型。
- 不以 Agent 取代规则、HTTP、审批和其他确定性工作流节点。

## 11. 评审待决事项

实施前必须确认：

1. Capability 是绑定固定工作流版本，还是允许受控地跟随最新发布版本；建议首期固定版本。
2. 哪些业务域允许 `AGENT_INPUT_ALLOWED`，其字段 Schema 由谁维护。
3. Agent 是否需要在工作流终态后自动恢复并面向用户生成结果；若需要，P2 的可靠唤醒不可省略。
4. 停止后的补偿策略是否由每个流程自行配置；建议复用工作流补偿节点，禁止平台自动猜测撤销方式。
5. Capability 与服务账号额度、业务应用空间的归属规则；建议以应用空间为最小授权单位。

## 12. 验证清单

- 领域服务单测：版本冻结、Schema 校验、授权交集、幂等、状态版本冲突、脱敏和结果映射。
- 工作流集成测试：启动入队、等待 Agent 输入、终止、失败节点重试、终态输出映射。
- 安全测试：跨 Agent、跨应用、跨 principal、非白名单变量、人工节点自动回答、敏感数据泄露。
- 恢复测试：重复工具调用、重复回调、Worker 重启、执行租约恢复、迟到的终态事件。
- API/前端验证：状态码本地化、未知码本地化兜底、SSE 断线后审计补拉。
- 构建验证：`mvn -pl admin -am test`，并执行新增模块的定向测试。
