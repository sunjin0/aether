# Agent 平台后续开发任务清单

> 版本：V0.1 文档基线
> 状态：草案（待评审确认）
> 范围：从 V0.1 到 V1.0 的详细开发任务、验收标准和风险

---

## 1. 任务总览

| 版本 | 主题 | 预计工期 | 前置版本 |
|------|------|----------|----------|
| V0.1 | 规划文档基线 | 1 天 | 无 |
| V0.2 | 数据模型、SQL、基础管理 CRUD | 3–5 天 | V0.1 |
| V0.3 | 普通聊天闭环 | 3–5 天 | V0.2 |
| V0.4 | SSE 流式响应 | 2–3 天 | V0.3 |
| V0.5 | 安全 HTTP 工具调用 | 3–5 天 | V0.4 |
| V0.6 | 运营审计和权限完善 | 2–3 天 | V0.5 |
| V0.7 | 工作流和知识库结构预留 | 1–2 天 | V0.6 |
| V1.0 | 基础可用平台版 | 2–3 天 | V0.7 |

---

## 2. V0.1 文档任务

### 2.1 已完成

- [x] 创建 `docs/agent-platform/` 目录
- [x] 创建 `ROADMAP.md`：版本路线图
- [x] 创建 `PRD.md`：产品需求文档
- [x] 创建 `DATABASE.md`：数据库设计方向
- [x] 创建 `API.md`：接口草案
- [x] 创建 `TASKS.md`：后续开发任务清单

### 2.2 待完成

- [ ] 文档评审会议（团队评审）
- [ ] 确认术语一致性（Agent、Model Provider、Tool、Conversation、Message、Run、Workflow、Knowledge Base）
- [ ] 确认路径一致性（API 前缀 `/api/agent/**`，表前缀 `agent_*`）
- [ ] 确认待确认问题（PRD 第 7 节）
- [ ] 文档基线冻结，进入 V0.2

**验收标准**：
- 五份文档通过评审，无阻塞性异议
- 术语、路径、版本定义在团队内达成一致

---

## 3. V0.2 数据模型和 CRUD 任务

### 3.1 数据模型（`api` 模块）

- [ ] 创建 `agent_model_provider` 实体（Entity）
- [ ] 创建 `agent_definition` 实体
- [ ] 创建 `agent_tool` 实体
- [ ] 创建 `agent_tool_binding` 实体
- [ ] 创建 `agent_conversation` 实体
- [ ] 创建 `agent_message` 实体
- [ ] 创建 `agent_run` 实体
- [ ] 创建 `agent_tool_call_log` 实体
- [ ] 创建 `agent_workflow` 实体（预留）
- [ ] 创建 `agent_knowledge_base` 实体（预留）
- [ ] 创建 `agent_document` 实体（预留）
- [ ] 创建对应 VO（View Object）
- [ ] 创建对应 DTO（Data Transfer Object）
- [ ] 创建 Mapper 接口（MyBatis-Plus `BaseMapper`）
- [ ] 创建 Service 接口

### 3.2 SQL 脚本

- [ ] 编写 `agent_model_provider` 建表脚本
- [ ] 编写 `agent_definition` 建表脚本
- [ ] 编写 `agent_tool` 建表脚本
- [ ] 编写 `agent_tool_binding` 建表脚本
- [ ] 编写 `agent_conversation` 建表脚本
- [ ] 编写 `agent_message` 建表脚本
- [ ] 编写 `agent_run` 建表脚本
- [ ] 编写 `agent_tool_call_log` 建表脚本
- [ ] 编写 `agent_workflow` 建表脚本（预留）
- [ ] 编写 `agent_knowledge_base` 建表脚本（预留）
- [ ] 编写 `agent_document` 建表脚本（预留）
- [ ] 索引创建脚本

### 3.3 Service 实现（`biz` 模块）

- [ ] 实现 `ModelProviderServiceImpl`
- [ ] 实现 `AgentDefinitionServiceImpl`
- [ ] 实现 `AgentToolServiceImpl`
- [ ] 实现 `AgentToolBindingServiceImpl`
- [ ] 实现 `ConversationServiceImpl`
- [ ] 实现 `MessageServiceImpl`
- [ ] 实现 `AgentRunServiceImpl`
- [ ] 实现 `AgentToolCallLogServiceImpl`

### 3.4 REST Controller（`admin` 模块）

- [ ] 实现 `ModelProviderController`（CRUD + 测试连接）
- [ ] 实现 `AgentDefinitionController`（CRUD + 复制）
- [ ] 实现 `AgentToolController`（CRUD + 测试）
- [ ] 实现 `AgentToolBindingController`（绑定、解绑、优先级）
- [ ] 实现 `ConversationController`（列表、详情、消息、关闭、删除）
- [ ] 实现 `AgentRunController`（列表、详情、统计）
- [ ] 实现 `AgentToolCallLogController`（列表、详情）

**验收标准**：
- 所有管理接口可通过 `WebResponse<T>` 正常返回
- 数据库表创建成功，软删除生效
- 基础权限拦截正常（`@Permission`）
- 单元测试覆盖 CRUD 核心路径

---

## 4. V0.3 普通聊天任务

### 4.1 模型调用客户端

- [ ] 设计模型调用客户端接口（`ModelClient`）
- [ ] 实现 OpenAI 兼容客户端（`OpenAIModelClient`）
- [ ] 支持配置化（API 地址、Key、模型、温度、最大 token）
- [ ] 支持超时和重试机制
- [ ] 错误处理：模型不可用、超时、内容审核

### 4.2 聊天服务

- [ ] 实现 `AgentChatService` 接口
- [ ] 实现 `AgentChatServiceImpl`：
  - 验证 Agent 和会话状态
  - 组装上下文（系统提示词 + 历史消息）
  - 调用模型 API
  - 持久化消息和运行记录
  - 返回模型回复
- [ ] 会话自动创建（首次对话时）
- [ ] 消息追加和上下文组装

### 4.3 聊天接口

- [ ] 实现 `POST /api/agent/chat`
- [ ] 请求参数校验
- [ ] 响应封装为 `WebResponse<MessageVO>`

**验收标准**：
- 可通过管理后台配置 Agent 后，调用聊天接口获得模型回复
- 消息正确写入数据库，关联到会话和 Agent
- 运行记录正确生成
- 模型调用失败时返回合理错误码

---

## 5. V0.4 SSE 流式任务

### 5.1 SSE 基础设施

- [ ] 研究 Spring Boot SSE 实现方案（`SseEmitter` 或 WebFlux）
- [ ] 设计 SSE 事件格式（与 `API.md` 草案一致）
- [ ] 实现 SSE 连接管理（超时、断开、异常）

### 5.2 流式聊天服务

- [ ] 扩展 `AgentChatService` 支持流式输出
- [ ] 实现流式消息分片处理
- [ ] 实现事件发送：`message`、`tool_call`、`error`、`done`
- [ ] 流式结束后持久化完整消息

### 5.3 流式聊天接口

- [ ] 实现 `GET /api/agent/chat/stream`
- [ ] 支持 Query 参数：`agentId`、`conversationId`、`message`
- [ ] 返回 `text/event-stream`

**验收标准**：
- 流式响应延迟 < 500ms（首 token）
- 事件类型与 `API.md` 草案一致
- 连接异常时客户端收到 `error` 事件并关闭
- 前端/客户端可正确解析 SSE 事件流

---

## 6. V0.5 安全工具任务

### 6.1 工具执行器

- [ ] 设计工具执行器接口（`ToolExecutor`）
- [ ] 实现 HTTP 工具执行器（`HttpToolExecutor`）
- [ ] 支持超时、重试、限流
- [ ] 支持请求头模板和请求体模板渲染
- [ ] 支持响应提取规则（JSONPath 或正则）

### 6.2 安全策略

- [ ] 实现 URL 白名单/黑名单校验
- [ ] 实现请求方法限制（GET/POST）
- [ ] 实现请求头过滤（禁止 `Authorization` 等敏感头）
- [ ] 实现响应大小限制
- [ ] 实现执行超时控制
- [ ] 安全拦截记录审计日志

### 6.3 工具调用闭环

- [ ] 扩展 `AgentChatService` 支持工具调用：
  - 模型返回 `tool_calls` 时，解析工具调用请求
  - 执行工具调用，获取结果
  - 将工具结果返回给模型继续生成
  - 持久化工具调用消息和运行记录
- [ ] 工具调用日志写入 `agent_tool_call_log`

### 6.4 工具管理增强

- [ ] 实现工具测试接口（手动触发）
- [ ] 实现安全策略配置界面（预留）

**验收标准**：
- 工具调用成功并返回结果给模型继续生成
- 恶意 URL 被拦截，记录审计日志
- 工具调用日志可查询、可追溯
- 超时工具调用返回错误，不阻塞会话

---

## 7. V0.6 运营审计任务

### 7.1 运行审计

- [ ] 确保每次 Agent 调用生成 `agent_run` 记录
- [ ] 实现运行记录查询接口（按 Agent、用户、时间范围）
- [ ] 实现运行统计接口（调用次数、token 消耗、平均耗时、错误率）
- [ ] 实现运行记录导出（预留）

### 7.2 会话审计

- [ ] 实现会话生命周期查询
- [ ] 实现消息统计（按会话、按 Agent）
- [ ] 实现会话活跃度分析（预留）

### 7.3 权限细化

- [ ] 定义 Agent 管理权限路径
- [ ] 定义模型供应商配置权限路径
- [ ] 定义工具管理权限路径
- [ ] 定义会话查看权限（仅本人/本部门/全部）
- [ ] 在 Controller 方法上添加 `@Permission` 注解
- [ ] 测试权限拦截

### 7.4 限流与配额（预留）

- [ ] 设计按用户限流方案
- [ ] 设计按 Agent 限流方案
- [ ] 预留限流配置表结构

**验收标准**：
- 每次 Agent 调用都有 `agent_run` 记录
- 权限路径与 `API.md` 草案一致
- 管理员可查看运营报表
- 无权限用户无法访问敏感接口

---

## 8. V0.7 预留能力任务

### 8.1 工作流结构预留

- [ ] 创建 `agent_workflow` 表（已包含在 V0.2 SQL 中）
- [ ] 实现 `AgentWorkflow` 实体、VO、Mapper、Service（空实现）
- [ ] 实现 `AgentWorkflowController`（返回 `501 Not Implemented` 或空列表）
- [ ] 编写工作流设计意图文档

### 8.2 知识库结构预留

- [ ] 创建 `agent_knowledge_base` 和 `agent_document` 表（已包含在 V0.2 SQL 中）
- [ ] 实现 `AgentKnowledgeBase`、`AgentDocument` 实体、VO、Mapper、Service（空实现）
- [ ] 实现 `AgentKnowledgeBaseController`、`AgentDocumentController`（返回 `501 Not Implemented` 或空列表）
- [ ] 编写知识库设计意图文档

**验收标准**：
- 表结构创建成功，不影响现有功能
- 接口占位返回合理状态码
- 后续版本可平滑扩展

---

## 9. V1.0 基础可用平台版任务

### 9.1 功能稳定

- [ ] 所有 V0.1–V0.7 功能稳定运行
- [ ] 修复所有阻塞性 Bug
- [ ] 完成回归测试

### 9.2 文档完善

- [ ] 生成 Swagger/OpenAPI 文档
- [ ] 编写部署文档（Docker、Jenkinsfile 更新）
- [ ] 编写用户手册（管理员配置、开发者接入）
- [ ] 更新 `ROADMAP.md` 为 V1.0 状态

### 9.3 监控与运维

- [ ] 实现调用量监控
- [ ] 实现错误率监控
- [ ] 实现延迟监控（P50、P95、P99）
- [ ] 集成现有监控体系（如有）

### 9.4 压力测试

- [ ] 设计压力测试方案
- [ ] 执行并发测试（100 并发）
- [ ] 优化性能瓶颈

**验收标准**：
- 通过压力测试（并发 100，响应时间 P99 < 5s）
- 无阻塞性 Bug
- 文档完整，新成员可独立部署和配置
- 监控体系可观测核心指标

---

## 10. Definition of Ready

每个版本开始前，需满足：

- [ ] 上一版本所有任务已完成并通过验收
- [ ] 本版本需求文档（PRD、API、DATABASE）已评审通过
- [ ] 技术方案已确认（无重大技术风险）
- [ ] 资源已分配（开发、测试、评审人员）
- [ ] 环境就绪（开发、测试、数据库）

---

## 11. Definition of Done

每个任务完成后，需满足：

- [ ] 代码已通过自测（本地运行通过）
- [ ] 单元测试覆盖核心路径（如有测试框架）
- [ ] 代码评审通过（至少 1 人评审）
- [ ] 文档已更新（接口文档、数据库文档如有变更）
- [ ] 无阻塞性 Bug
- [ ] 已合并到主分支（或开发分支）

---

## 12. 验证清单

### 12.1 文档验证

- [ ] 五份文档文件存在且内容完整
- [ ] 术语一致性：Agent、Model Provider、Tool、Conversation、Message、Run、Workflow、Knowledge Base
- [ ] 路径一致性：API 前缀 `/api/agent/**`，表前缀 `agent_*`
- [ ] 分页参数一致性：使用 `current`/`pageSize`（非 `page`/`size`）
- [ ] 版本定义与任务清单一致

### 12.2 代码验证（每版本）

- [ ] `mvn clean compile` 通过
- [ ] `mvn test` 通过（如有测试）
- [ ] 管理接口可正常访问（本地启动验证）
- [ ] 数据库表创建成功
- [ ] 权限拦截正常

### 12.3 集成验证（V0.3 及以后）

- [ ] 聊天接口可正常返回模型回复
- [ ] SSE 流式响应正常
- [ ] 工具调用闭环正常
- [ ] 运行记录和工具调用日志正确生成

---

## 13. 风险与应对

| 风险 | 影响 | 应对策略 |
|------|------|----------|
| 模型供应商 API 变更 | 高 | 封装模型调用客户端，隔离供应商差异；关注供应商变更公告 |
| 工具调用安全风险 | 高 | V0.5 严格安全策略；URL 白名单；请求头过滤；审计日志 |
| 并发性能瓶颈 | 中 | V0.4 使用 SSE 异步；V1.0 压测优化；考虑连接池和缓存 |
| 需求变更 | 中 | 每版本评审；文档先行；避免返工；预留扩展点 |
| 权限模型复杂化 | 低 | 复用现有 `@Permission` 体系；逐步细化；不推翻重来 |
| 团队成员变动 | 低 | 文档完善；代码规范；任务清单清晰；知识沉淀 |

---

## 14. 变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| V0.1 | 2026-06-22 | 初始草案，定义 V0.1–V1.0 全部任务 |
