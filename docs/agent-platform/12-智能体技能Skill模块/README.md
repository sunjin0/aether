# Agent 平台 — 智能体技能（Skill）模块方案

> 更新日期：2026-08-08
> 状态：已实现（数据库 V38-V42、Skill CRUD / 草稿 / 版本 / 发布、工具与知识库绑定、Agent-Skill 安装绑定、资源上传/列表/删除（对象存储）、提示词合成与工具收敛已接入 AgentChat、前端资源管理与提示词预览 UI、后端 Skill 单测）。运行期会校验已冻结资源的存在性、启用状态和 SHA-256，将受限 Markdown/模板参考注入提示词，按输入 Schema 校验并脱敏 `skillInputs`，并在 `agent_run.skill_snapshot` 冻结版本、资源、最小授权范围、脱敏输入和预算明细；已安装 Skill 的完整上下文超出模型输入预算时会在调用前拒绝，不会静默截断 Skill 指令。V41-V42 已新增受控沙箱产物执行能力，仍保持脚本不在 Admin JVM 或模型侧直接执行。
> 范围：Skill 模块功能方案设计、数据模型、运行机制、接口与实施规划。

---

## 一、背景与目标

### 1.1 现状

当前平台已具备以下与"技能"相邻的能力，但缺少统一的能力封装层：

| 已有能力 | 作用 | 局限 |
| --- | --- | --- |
| `AgentDefinition` | Agent 身份、系统提示词、模型参数、执行模式 | 提示词为单体文本，无法复用、组合或按版本管理 |
| `AgentTool` + 绑定 | 授予 Agent MCP 工具能力，含风险审批与审计 | 工具是执行能力，不含任务规范与输入契约 |
| `agent_knowledge_base_binding` | 为 Agent 授权知识库，参与 RAG | 只有"库级"开关，没有能力级限定 |
| `AgentWorkflowTemplate` | 复用工作流画布 | 面向流程编排，不是聊天能力包 |

### 1.2 目标

Skill 模块将"领域知识 + 执行规范 + 资源依赖"封装为**可版本化、可审批、可复用**的技能包，由管理员配置后绑定到 Agent。Agent 对话时按绑定的 Skill 版本解析并合入上下文，不改变现有聊天、MCP 审批、Deep Agent 回调与审计链路。

### 1.3 边界（已确认）

- Skill 仅由**管理员创建、发布并绑定到 Agent**；终端用户聊天时不主动选择 Skill，由平台按 Agent 绑定自动装配。
- Skill 支持绑定受限知识库，作为 Agent 检索范围的收窄条件。
- Skill 支持上传 Markdown、脚本与模板文件，作为版本化资源管理；已发布资源使用不可覆盖的对象存储键冻结内容。
- 脚本作为受版本控制的资源供模型参考；受控产物执行已作为二期能力上线，仅经短期委派令牌和独立 Sandbox Runner 执行。
- 工具调用、知识库访问仍遵循最小授权；Skill 只收窄、不扩权。

### 1.4 非目标

- 不提供通用远程代码执行能力；脚本只能作为已发布 Skill 的声明式受控产物执行入口，经独立 Sandbox Runner 执行。
- 不将 Skill 暴露为无治理的原生模型工具。
- 不复制或绕过 `agent_mcp_server` / `agent_tool` 的凭证与安全模型。
- 不支持 Skill 之间的动态递归调用或复杂编排图。
- 一期不修改外部 Deep Agent 服务协议。

---

## 二、术语

| 术语 | 说明 |
| --- | --- |
| Skill | 技能主记录，表示稳定的能力身份（名称、编码、分类、状态、当前版本） |
| Skill 版本 | 不可变快照，含指令、契约、资源、工具与知识库声明 |
| 资源（Resource） | Skill 版本内上传的 Markdown、脚本、模板文件；内容由 SHA-256 和不可覆盖对象键共同标识 |
| Agent-Skill 绑定 | 将某 Skill 的某个已发布版本安装到 Agent，含优先级与配置覆盖 |
| 权限收敛 | 最终可用工具/知识库 = Agent 已授权 ∩ Skill 声明 |
| 最终装配上下文 | 一次请求经校验后冻结的 Skill、工具、知识库、资源和提示词集合，所有运行链路只能消费该集合 |

---

## 三、为什么需要版本控制

版本控制不是增加复杂度，而是保障**运行可复现、变更可控、安全可审计**：

1. **运行可复现**：Deep Agent 任务可能执行较长时间。管理员在任务执行期间修改 Skill 后，正在运行的任务不能静默使用新内容。
2. **变更可控**：Agent 绑定的是明确的 Skill 版本，Skill 编辑或新版本发布不会在未审批、未升级时改变生产行为。
3. **安全回滚**：某次更新导致输出质量下降或引入错误规则时，可将 Agent 从 `v2` 一键切回 `v1`。
4. **审计可追溯**：`agent_run` 能还原"当时执行的是哪个版本、哪个脚本、哪些工具与知识库"，支撑质量分析与问题定位。
5. **治理合规**：尤其是关联 MCP 工具或脚本资源时，未审核的变更不得直接影响线上 Agent。

采用轻量版本模型：只允许编辑草稿；发布生成不可变版本号（`v1`、`v2`…）；Agent 绑定明确版本；新版发布后由管理员显式升级。

---

## 四、为什么绑定既有 MCP 工具

Skill 不自行配置或直接调用 MCP，只**声明依赖哪些既有工具**，最终范围由 Agent 授权决定。原因：

- **防止权限绕过**：Skill 不能通过自身配置获得 Agent 未被授予的工具。
- **避免凭证重复**：MCP 地址、认证密钥、传输方式已由 `agent_mcp_server` 管理，Skill 不应保存第二份。
- **保留现有审批与审计**：工具调用仍走 `ToolCallRiskAnalyzer`、用户确认、10 分钟临时授权、幂等键与 `agent_tool_call_log`。
- **兼容 Deep Agent**：收敛后的工具白名单直接映射为 `allowed_tools` 与短期委派 JWT，无需改动外部协议。
- **便于治理**：工具被停用、MCP 服务异常或权限收回后，依赖它的 Skill 自动失效或在预校验阶段报错。
- **能力复用**：一个工具可被多个 Skill 使用，一个 Skill 可适配不同 Agent 的授权范围。

权限收敛规则：

```text
最终可用工具   = 已启用 Agent 工具绑定 ∩ Skill 版本声明的工具 ∩ 运行模式允许范围
最终知识库范围 = Agent 已授权知识库    ∩ Skill 版本声明的知识库
```

Skill 对工具与知识库是**收窄和声明依赖**，不是扩权。

---

## 五、数据模型设计

### 5.1 实体关系

```text
agent_skill (主记录)
  ├── 1:N agent_skill_version (不可变版本)
  │     ├── 1:N agent_skill_resource (版本资源)
  │     ├── 1:N agent_skill_tool_binding (版本工具声明)
  │     └── 1:N agent_skill_knowledge_binding (版本知识库声明)
  └── 1:N agent_definition_skill_binding (Agent 安装项)
```

### 5.2 `agent_skill` — Skill 主记录

| 字段 | 说明 |
| --- | --- |
| `name` | Skill 名称 |
| `code` | 唯一编码，`uk_code` |
| `description` | 用途说明 |
| `category` | 分类（制度问答、风险评估、工单查询等） |
| `status` | `0` 草稿、`1` 已启用、`2` 已停用 |
| `current_version_id` | 当前已发布版本 ID |
| `icon` / `tags` / `sort_num` | 展示与排序 |

继承 `BaseEntity`（`id`、`created_at`、`updated_at`、`sort_num`、`deleted`、`state`）。

### 5.3 `agent_skill_version` — 不可变版本

| 字段 | 说明 |
| --- | --- |
| `skill_id` | 所属 Skill |
| `version_no` | 版本号（`1`、`2`…），`uk(skill_id, version_no)` |
| `instruction` | 领域指令（Markdown） |
| `input_schema` | 输入契约 JSON（JSON Schema） |
| `output_schema` | 输出契约 JSON（JSON Schema，一期作为约束与元数据） |
| `tool_policy` | `required` / `optional` 等策略说明（字段由工具绑定表表达） |
| `status` | `0` 草稿、`1` 已发布 |
| `change_note` | 变更说明 |
| `published_at` / `published_by` | 发布信息 |

发布后**禁止修改与删除**，仅可逻辑停用。

草稿和发布模型：`agent_skill_version` 同时承载草稿和发布快照；一个 Skill 最多一个 `DRAFT` 版本，草稿可编辑且不分配正式 `version_no`。发布时在同一事务内复制草稿及全部子项为新的 `PUBLISHED` 版本、分配递增 `version_no`，随后删除或重置草稿；已发布记录及其子项绝不更新。

### 5.4 `agent_skill_resource` — 版本资源

| 字段 | 说明 |
| --- | --- |
| `skill_version_id` | 所属版本 |
| `name` | 资源名称 |
| `type` | `MARKDOWN` / `SCRIPT` / `TEMPLATE` |
| `language` | 脚本语言（`.js` / `.py` / `.sh`） |
| `object_key` | 不可覆盖对象地址，例如 `skills/{skill_id}/{version_id}/{sha256}`；发布后禁止替换和删除 |
| `content_sha256` | 文件哈希，用于校验与审计 |
| `size` | 文件大小 |
| `purpose` | 用途说明（参考规则 / 输出模板 / 处理脚本） |
| `status` | `0` 禁用、`1` 启用 |

上传时校验扩展名与 MIME、单文件/单版本总大小和资源数量；拒绝压缩包及不在白名单内的类型。发布事务中复核对象 SHA-256 后冻结对象键。脚本默认不将完整内容注入模型；其二期受控执行仅可由已发布版本的声明式配置触发。

### 5.5 `agent_skill_tool_binding` — 版本工具声明

| 字段 | 说明 |
| --- | --- |
| `skill_version_id` | 所属版本 |
| `tool_id` | 引用 `agent_tool.id` |
| `required` | 是否必需工具 |
| `priority` | 建议优先级 |

唯一索引 `uk(skill_version_id, tool_id)`。工具状态、MCP 服务状态实时校验，不复制凭证。

### 5.6 `agent_skill_knowledge_binding` — 版本知识库声明

| 字段 | 说明 |
| --- | --- |
| `skill_version_id` | 所属版本 |
| `knowledge_base_id` | 引用 `knowledge_base.id` |

运行期最终范围 = Agent 已授权知识库 ∩ 此处声明。

### 5.7 `agent_definition_skill_binding` — Agent 安装项

| 字段 | 说明 |
| --- | --- |
| `agent_definition_id` | 所属 Agent |
| `skill_id` | 所属 Skill |
| `skill_version_id` | **安装的明确版本**，不自动跟随最新 |
| `priority` | 装配顺序 |
| `status` | `0` 停用、`1` 启用 |
| `config_overrides` | 可选参数覆盖 JSON |

唯一索引 `uk(agent_definition_id, skill_id)`。

### 5.8 运行快照

在 `agent_run` 增加 `skill_snapshot TEXT`，在任何模型调用、检索或外部 Deep 请求前写入。快照记录实际版本和绑定、脱敏输入、资源对象键与 SHA-256、最终工具/知识库 ID、合成提示词 SHA-256、预算与裁剪结果；不保存完整敏感输入或资源正文。读取运行详情即可还原真实执行上下文。

---

## 六、运行时语义

### 6.1 装配流程

普通 Agent 与 Deep Agent 共享统一装配入口：

1. 校验当前用户、Agent 启用状态与执行模式；聊天请求不能指定、跳过或替换 Skill。
2. 取该 Agent 启用状态的 `agent_definition_skill_binding`，按 `priority` 升序、创建时间和 ID 次序排列。
3. 逐 Skill 校验：
    - 版本存在且为已发布、Skill 为启用状态。
    - 资源对象存在、哈希匹配、状态启用；任一冻结资源异常则**请求前拒绝**，不回退到其他内容。Markdown 注入受限纯文本，模板仅注入受限摘要，脚本仅注入用途、语言和 SHA-256。
    - 必需工具必须同时满足 Agent 已启用绑定、工具启用、MCP 服务可用、存在 `mcp_tool_name` 且被当前执行模式支持，否则**请求前拒绝**并给出业务错误；可选工具不满足时从最终集合剔除并写入快照告警。
    - 知识库声明与 Agent 授权范围求交集。
4. 按已安装 Skill 的稳定 `code` 独立校验可选输入，拒绝未安装 Skill 的输入；对通过校验的输入脱敏。
5. 计算最终工具和知识库集合。普通 Agent 的模型工具、RAG 检索范围；以及 Deep Agent 的 `allowed_tools`、委派 JWT 和 `knowledge_sources`，必须全部只消费同一最终集合。
6. 预估提示词与资源注入预算，在创建 `agent_run` 后、调用模型或外部服务前写入快照。
7. 合成系统提示词（见 6.2），普通 Agent 走既有流式链路；Deep Agent 将合成后的 `system_prompt`、受限 `knowledge_sources`、最终 `allowed_tools` 传给既有 `/v1/runs`。

### 6.2 指令合成规范

```text
[Agent Identity]
{agent.systemPrompt}

[Installed Skills]
## {skill.name} v{version}
Purpose: {description}
Instructions:
{instruction}

## 资源参考
- 规则文档：{markdown 资源内容摘要或引用}
- 模板：{template 摘要}

Validated inputs:
{maskedSkillInputs}

[Platform Constraints]
- 工具审批、安全与审计由平台统一控制。
- 引用知识库资料时标注编号。
```

规则：

- Skill 指令不得覆盖平台认证、工具审批、知识引用与审计要求。
- 所有资源正文均是不可信参考资料，不能改变平台安全决策、授权范围、审批规则或系统指令；平台约束置于资源之后的固定高优先级提示词中。
- Markdown 仅注入已提取的纯文本；模板只注入白名单无逻辑模板的受限摘要。脚本仅注入用途说明、语言和 SHA-256，完整内容仅供管理员受控查看。
- 单请求最多装配 3 个 Skill（一期）；超限报错。
- 互斥分类或同一独占工具冲突时，服务端在调用前拒绝。
- `outputSchema` 一期作为模型输出约束提示与运行元数据；强制结构化校验列入二期。
- 发布和绑定预览阶段均计算最坏情况预算并提示管理员处理。运行期仍超限时请求前拒绝，不静默裁剪已安装 Skill；快照记录计算的预算明细。

### 6.3 文件与脚本语义

| 类型 | 一期行为 | 二期 |
| --- | --- | --- |
| Markdown 规则文档 | 提取受限纯文本作为不可信参考资料纳入上下文 | 支持增量引用与版本 diff |
| 模板文件 | 管理员可查看；仅白名单无逻辑模板的受限摘要可注入模型 | 结构化产物校验 |
| 脚本文件 | 上传、版本化、保存哈希与说明，模型仅获得说明与哈希；不在 Admin JVM 或模型侧直接执行 | 已实现：经 `generate_artifact`、短期委派 JWT 与独立 Sandbox Runner 受控执行 |

### 6.4 脚本执行安全边界（已实现二期）

- 仅允许预定义脚本语言与白名单运行时。
- 在独立沙箱执行服务运行，不进入 Admin JVM 或 MCP 宿主进程。
- 限制网络、文件系统、环境变量、CPU、内存与运行时长。
- 定义严格 JSON 输入/输出契约，输出大小受限。
- 以受管 MCP 工具（如 `skill_script_execute`）暴露，继续走审批、委派 JWT 与幂等机制。
- 完整审计：脚本版本、输入摘要、执行结果、耗时与失败原因。

---

## 七、管理与发布流程

1. 管理员创建 Skill 主记录和唯一一个可编辑草稿版本，填写基础信息、领域指令、输入/输出契约。草稿版本是资源、工具和知识库声明的唯一编辑载体。
2. 在草稿版本绑定既有工具（声明依赖）与受限知识库，上传 Markdown / 脚本 / 模板资源。
3. 发布前校验 JSON Schema、资源类型/限额/对象哈希、工具与知识库状态、互斥规则和预算。
4. 发布：在事务和 Skill 行锁内分配递增版本号、复制并冻结草稿及全部子项，复核对象 SHA-256；发布失败回滚数据库变更并保留草稿。已发布版本不可修改、删除或覆盖资源。
5. 主记录指向最新已发布版本；新发布版本不会改变已有 Agent 安装项。
6. Agent 配置页安装指定版本，配置优先级与参数覆盖。
7. 运行期按安装版本执行；升级通过"升级到最新版"显式完成。
8. 停用 Skill 后禁止新聊天装配；历史运行与绑定快照仍可查询。运行中的请求继续使用其已冻结快照。
9. 删除仅允许未被 Agent 或运行审计引用的草稿；已发布版本仅逻辑停用。每个 Skill 同时最多存在一个草稿版本。

---

## 八、权限与审计

| 权限路径 | 用途 |
| --- | --- |
| `/agent/skill` 读 | 查看、查询、预览 Skill |
| `/agent/skill` 写 | 创建、编辑草稿、发布、停用、版本管理 |
| `/agent/definition` 写 | 为 Agent 安装、移除、升级 Skill |
| `/agent/chat` | 仅能在已授权 Agent 上调用其已安装 Skill |

审计要求：

- `agent_run.skill_snapshot` 记录实际版本、绑定、脱敏输入与资源摘要。
- 工具调用沿用 `agent_tool_call_log` 审计。
- 发布、停用、Agent 安装/升级等操作记录操作日志。
- 脚本资源存储哈希与版本，防止上传后内容被替换。
- 权限资源按既有 RBAC 模型增加 `/agent/skill` 路由与 `perm_agent_skill_read`、`perm_agent_skill_write` 叶子权限；模块权限为平台级权限，不以创建人为资源归属。

---

## 九、API 草案

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/agent/skill/list` | 分页查询，支持名称、编码、分类、状态筛选 |
| `GET` | `/api/agent/skill/{id}` | Skill 详情及当前版本 |
| `POST` | `/api/agent/skill` | 创建草稿 |
| `PUT` | `/api/agent/skill/{id}` | 编辑草稿基本信息 |
| `POST` | `/api/agent/skill/{id}/draft` | 创建下一草稿版本（每个 Skill 最多一个） |
| `GET` | `/api/agent/skill/{id}/versions` | 版本历史 |
| `POST` | `/api/agent/skill/{id}/versions/{version}/publish` | 发布版本 |
| `PUT` | `/api/agent/skill/{id}/status` | 启用/停用 |
| `POST` | `/api/agent/skill/{id}/resources` | 上传资源 |
| `GET` | `/api/agent/skill/{id}/resources` | 资源列表 |
| `POST` | `/api/agent/skill/{id}/preview` | 使用样例输入预览合成提示词，不调用模型 |
| `GET` | `/api/agent/definition/{agentId}/skills` | 查询 Agent 的 Skill 安装项 |
| `POST` | `/api/agent/definition/{agentId}/skills` | 安装指定版本 |
| `PUT` | `/api/agent/definition/{agentId}/skills/{bindingId}` | 调整优先级、启停、升级版本 |
| `DELETE` | `/api/agent/definition/{agentId}/skills/{bindingId}` | 卸载 Skill |

聊天 DTO 增量字段（可选，仅承载已自动装配 Skill 的输入）：

```java
private Map<String, Map<String, Object>> skillInputs;
```

外层 key 必须是已安装 Skill 的 `code`，内层对象按对应版本的 `input_schema` 校验。请求不得携带 `skillIds` 或其他选择/禁用字段；未传 `skillInputs` 时仍自动装配该 Agent 所有启用 Skill，并按各版本 Schema 处理缺省值和必填项。

---

## 十、数据库迁移建议

新增 Flyway 迁移 `V38__agent_skill_module.sql`（当前迁移基线为 V37）：

- `agent_skill`
- `agent_skill_version`（`uk(skill_id, version_no)`）
- `agent_skill_resource`
- `agent_skill_tool_binding`（`uk(skill_version_id, tool_id)`）
- `agent_skill_knowledge_binding`
- `agent_definition_skill_binding`（`uk(agent_definition_id, skill_id)`）
- `agent_run` 增加 `skill_snapshot TEXT`
- 权限资源、菜单与 root 角色授权种子

所有表继承 `BaseEntity` 字段约定，主键为 `VARCHAR(32)` 雪花 ID，时间戳 `BIGINT` 毫秒，`deleted` 逻辑删除。

---

## 十一、代码落点

| 模块 | 改动范围 |
| --- | --- |
| `api` | `agent.skill` 包：Entity、DTO、VO、Mapper、Service 接口；Flyway 迁移；扩展 `AgentChatDto`、`AgentRun` |
| `biz` | Skill CRUD/发布服务；Agent-Skill 绑定服务；`SkillContextService`（校验、资源完整性、权限收敛、预算、指令合成、快照）；先于模型调用创建标准 `agent_run`，接入 `AgentChatServiceImpl` 与 `DeepAgentRunService`；扩展 `KnowledgeContextService` 以接收最终知识库 ID 集合 |
| `admin` | `AgentSkillController`；扩展 Agent 绑定与聊天入口参数校验 |
| `common` | 通用 JSON Schema 校验或新的 i18n 错误码（如可复用） |
| `docs` | 更新业务说明、架构设计、数据库设计、API 参考与前端对接说明 |

---

## 十二、测试与验收

- Skill 草稿、版本、发布、停用、逻辑删除与版本不可变性测试。
- Agent 仅能绑定已发布、已启用的 Skill 版本。
- Agent 只能装配自身已安装的 Skill，不能越权选择。
- 输入 Schema 校验、必需工具缺失、知识库越权、互斥 Skill 拒绝场景。
- 多 Skill 指令合成顺序、预算拒绝与脱敏验证。
- 普通 Agent 工具调用仍经审批与审计。
- Deep Agent 请求中 `system_prompt`、`allowed_tools`、`knowledge_sources` 为收敛后集合。
- `agent_run` 记录准确 Skill 版本与输入快照。
- 普通 Agent 在首次模型调用、RAG 检索或工具审批前创建运行记录并冻结快照；Deep Agent 的外发请求、委派 JWT、工具白名单和知识来源均与快照一致。
- 拒绝客户端指定/跳过 Skill、未安装 Skill 输入、资源哈希不匹配、资源类型或预算超限的场景。
- 不传 Skill 的既有聊天、Deep Agent 与工作流回归测试。
- 执行 `mvn -pl admin -am -DskipTests compile`、`mvn -pl biz -am test`，并补充控制器与服务单测。

---

## 十三、实施批次

| 批次 | 内容 |
| --- | --- |
| 1. 基础治理 | Skill、Version、资源、工具/知识库声明、Agent 绑定、权限资源、CRUD、发布/停用与预览 |
| 2. 标准聊天接入 | 输入校验、指令合成、工具/知识库权限收敛、运行快照 |
| 3. Deep Agent 接入 | 将解析结果映射到既有 Deep Run 请求，补齐回归测试 |
| 4. 二期增强 | 已实现脚本沙箱产物执行；待实现 Skill 市场/分类推荐、版本 diff、结构化输出强校验、Skill 指标与导入导出 |

---

## 十四、开放问题

1. `config_overrides` 一期允许覆盖哪些字段（如 temperature、maxTokens）？建议仅允许白名单字段，且不能覆盖平台安全约束、工具或知识库范围。
2. Skill 绑定知识库是否需要支持版本级「库内文档/章节」限定，还是仅库级？
3. 模板资源的一期受限摘要采用何种白名单格式及最大注入长度？脚本一期固定只注入说明与哈希。
4. 是否需要在 Skill 市场页按分类/评分推荐给管理员？

---

## 十五、参考资料

- 业务说明：`docs/agent-platform/00-项目文档/02-业务说明/README.md`
- 架构设计：`docs/agent-platform/00-项目文档/03-架构设计/README.md`
- 数据库设计：`docs/agent-platform/00-项目文档/04-数据库设计/README.md`
- API 参考：`docs/agent-platform/00-项目文档/05-API参考/README.md`
