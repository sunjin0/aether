# 项目文档

> 最后整理：2026-08-05

本目录收纳 Agent 平台的正式项目文档。每个主题独立成目录并以 `README.md` 作为入口；具体前端对接专题请查阅同级 `01-10` 目录。

| 分类 | 文档 | 内容 |
| --- | --- | --- |
| 项目总览 | [项目总体说明](01-项目总体说明/README.md) | 模块、整体架构、部署关系、基础服务和排障入口。 |
| 产品业务 | [业务说明](02-业务说明/README.md) | 业务对象、核心流程、治理规则、场景和指标。 |
| 技术架构 | [架构设计](03-架构设计/README.md) | 分层、认证、RAG、Deep Agent、工作流和 Redis。 |
| 数据模型 | [数据库设计](04-数据库设计/README.md) | 表结构、索引、状态字典和 Flyway 演进。 |
| 接口契约 | [API 参考](05-API参考/README.md) | REST、权限路径、SSE、回调与公开端点。 |
| 外部集成 | [工作流业务集成](06-工作流业务集成/README.md) | 服务账号、Webhook、幂等启动和回调验签。 |
| 技术方案 | [历史对话性能优化](07-历史对话性能优化/README.md) | 上下文缓存、摘要与性能优化。 |

## 关联方案

- 智能体技能（Skill）模块方案位于同级 `12-智能体技能Skill模块/README.md`（Agent 平台文档索引见 `docs/README.md`）。

## 使用约定

- 数据库结构以 `api/src/main/resources/db/migration/postgresql/` 的 Flyway 迁移为准。
- HTTP 接口与权限路径以 `admin/src/main/java/com/aether/**/controller/` 为准。
- 运行机制以 `biz/` 与 `common/` 当前实现为准。
