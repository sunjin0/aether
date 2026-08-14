# 文档索引

> 最后整理：2026-08-11

本目录按文档用途分类。正式设计与接口说明应以当前代码、控制器及 Flyway 迁移为准；`superpowers/` 下内容为历史设计和实施记录，不应作为当前接口契约。

## 产品与业务

| 文档 | 用途 |
| --- | --- |
| [业务说明](agent-platform/00-项目文档/02-业务说明/README.md) | 产品定位、业务对象、核心流程、治理规则、典型场景和衡量指标。 |
| [Agent 平台产品与规划](agent-platform/01-产品与规划/README.md) | Agent 平台版本路线、能力边界和产品规划。 |

## 技术设计与数据

| 文档 | 用途 |
| --- | --- |
| [项目总体说明](agent-platform/00-项目文档/01-项目总体说明/README.md) | 项目模块、整体架构、部署关系、基础服务和排障入口。 |
| [架构设计](agent-platform/00-项目文档/03-架构设计/README.md) | 分层、认证授权、RAG、Deep Agent、工作流和 Redis 设计。 |
| [数据库设计](agent-platform/00-项目文档/04-数据库设计/README.md) | 业务表、索引、状态字典及 Flyway V1-V32 演进。 |
| [Agent 平台架构设计](agent-platform/02-架构设计/README.md) | 前端平台视角的模块、SSE、权限和基础数据模型。 |
| [Deep Agent 动态规划与可恢复执行](agent/动态任务规划与可恢复执行方案.md) | PostgreSQL 检查点、动态计划、暂停和继续执行方案。 |

## 接口与集成

| 文档 | 用途 |
| --- | --- |
| [API 参考](agent-platform/00-项目文档/05-API参考/README.md) | REST 端点、权限路径、SSE 事件、Deep 回调和出站回调。 |
| [工作流业务集成](agent-platform/00-项目文档/06-工作流业务集成/README.md) | 服务账号、Webhook、幂等启动和业务回调验签。 |
| [前端通用对接](agent-platform/03-前端通用对接/README.md) | 前端通用约定、认证、错误处理与文件能力。 |
| [前端知识库对接](agent-platform/04-前端知识库对接/README.md) | 知识库、文档版本、审核、AI 审查和检索评测。 |
| [前端 MCP 工具对接](agent-platform/05-前端MCP工具对接/README.md) | MCP 工具、审批与调用日志。 |
| [前端交互式提问对接](agent-platform/06-前端交互式提问对接/README.md) | 普通与 Deep Agent 的交互卡片和恢复流程。 |
| [知识库 AI 审查](agent-platform/08-知识库AI审查/README.md) | AI 补丁建议、Diff、采纳与冲突处理。 |
| [前端管理员偏好对接](agent-platform/10-前端管理员偏好对接/README.md) | 管理员偏好功能的前端集成说明。 |
| [RAG 检索评测优化方案](agent-platform/11-RAG评测优化方案/README.md) | 检索评测的数据集版本、运行快照、异步任务、指标、诊断与实施方案。 |
| [智能体技能 Skill 模块方案](agent-platform/12-智能体技能Skill模块/README.md) | Skill 技能包、版本控制、资源、工具与知识库权限收敛、脚本安全边界及实施方案。 |
| [模型目录与供应商管理](agent-platform/13-模型目录与供应商管理/README.md) | 供应商连接、模型目录、能力约束、运行配置、批量导入、诊断和排障说明。 |

## 运维与版本记录

| 文档 | 用途 |
| --- | --- |
| [前端版本变更日志](agent-platform/07-前端版本变更日志/README.md) | Agent 平台 V0.x-V1.5 前端接口和能力变更。 |
| [运维手册](agent-platform/09-运维手册/README.md) | 环境变量、数据库迁移、运行维护与数据保留。 |
| [历史对话性能优化方案](agent-platform/00-项目文档/07-历史对话性能优化/README.md) | 对话上下文缓存、摘要和性能优化方案。 |

## 测试资料

| 目录 | 用途 |
| --- | --- |
| [`rag-test/`](rag-test/) | RAG 测试语料、问题与标准答案，以及生成测试文档的脚本。 |

## 历史设计与实施记录

| 目录 | 用途 |
| --- | --- |
| [`superpowers/specs/`](superpowers/specs/) | 按日期归档的设计规格。 |
| [`superpowers/plans/`](superpowers/plans/) | 按日期归档的实施计划、复盘和验收记录。 |

## 使用约定

- 数据库结构以 `api/src/main/resources/db/migration/postgresql/` 的 Flyway 迁移为准。
- HTTP 接口与权限路径以 `admin/src/main/java/com/aether/**/controller/` 为准。
- 文档涉及运行机制时，以 `biz/` 和 `common/` 的当前实现为准。
- 修改或新增正式功能时，同时更新对应的业务、接口或前端集成文档。
