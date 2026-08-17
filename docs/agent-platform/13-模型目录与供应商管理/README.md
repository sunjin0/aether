# 模型目录与供应商管理

> 更新日期：2026-08-11  
> 适用范围：模型供应商、Agent 对话、知识库向量检索/查询重写/Rerank、AI 审查和 Skill 路由。

## 1. 目标与概念

模型供应商只保存连接信息：协议类型、基础地址、API Key、状态和备注。实际可被业务选择的模型由模型目录管理，避免把聊天、向量和
Rerank 模型混用。

一个目录项关联一个供应商，保存模型名称、能力、上下文窗口、端点覆盖、状态、排序和备注。运行时由目录项解析得到供应商连接与实际模型名称，API
Key 不会返回给前端或写入运行快照。

当前支持的能力标签：

| 能力                | 使用位置                      |
|-------------------|---------------------------|
| `CHAT`            | Agent 对话、查询重写、AI 审查       |
| `MULTIMODAL`      | Agent 对话、查询重写、AI 审查       |
| `EMBEDDING`       | 知识库向量索引/检索、Skill 路由索引     |
| `RERANK`          | 知识库 Rerank                |
| `VIDEO` / `AUDIO` | 当前用于目录管理与能力约束，尚未扩展独立业务调用链 |

业务选择器仅返回符合所需能力、模型状态为启用且供应商状态为启用的目录项，显示格式为“模型名（供应商名）”。后端在保存配置和运行解析时重复校验，不能仅依赖前端筛选。

## 2. 数据与迁移

Flyway `V45__agent_model_catalog.sql` 新增 `agent_model_catalog`：

| 字段                           | 说明             |
|------------------------------|----------------|
| `provider_id`、`name`         | 供应商与实际请求模型名    |
| `capabilities`               | 逗号分隔的能力集合      |
| `context_window`             | 目录声明的上下文窗口     |
| `endpoint_override`          | 该模型专用的调用地址，可为空 |
| `status`、`sort_num`、`remark` | 生命周期和展示信息      |

同一供应商下，未删除记录的 `name` 唯一。迁移同时为 `agent_definition` 增加 `model_id`、为 `knowledge_base` 增加
`embedding_model_id`。

迁移会将历史供应商的 `default_model` 建立为目录项；名称无法可靠识别的记录能力为 `UNCONFIRMED`，不能用于新配置或运行。当前运行链路不回退到旧的
`providerId + model` 配置，管理员需在供应商工作台为实际模型补全能力后重新配置业务对象。

## 3. 管理工作台

路径：`/agent/model-provider`。

- 左侧展示供应商连接，可选择、创建、编辑、启停、删除和测试连接。
- 右侧展示连接详情（包含创建/修改时间）和模型目录。
- “新增目录模型”用于手工维护单个模型；模型名称为单值。
- “获取模型列表”从当前供应商读取模型后，以卡片展示。已存在于目录的模型显示“已添加”且不可再次选择。
- 每张待导入模型卡片独立选择能力；保存前必须为每个选中项配置能力。
- “保存所选模型”调用事务批量保存接口。任一条校验或落库失败时整体回滚，避免部分导入。

供应商连接测试使用 `OPTIONS` 请求，携带 Bearer API Key，连接超时为 5 秒、读取超时为 10 秒。只有成功 HTTP
响应才显示连接成功；认证失败、地址不存在和超时均显示失败及耗时。

阿里云百炼的 OpenAI 兼容地址没有稳定的 `/v1/models` 枚举契约。对于 `aliyuncs.com` 地址，服务端返回内置的 Qwen 常用模型候选；其他
OpenAI 兼容供应商请求其 `/v1/models` 或 `/models`。

## 4. 接口

权限路径均为 `/agent/model-provider`。查询接口使用 Read 权限；目录写入、启停和删除使用 Write 权限。

| 方法                        | 地址                                                        | 说明                                        |
|---------------------------|-----------------------------------------------------------|-------------------------------------------|
| `POST`                    | `/api/agent/model-provider/list`                          | 分页查询供应商                                   |
| `GET`                     | `/api/agent/model-provider/{id}`                          | 供应商详情，API Key 不返回                         |
| `POST` / `PUT` / `DELETE` | `/api/agent/model-provider`、`/{id}`                       | 供应商维护                                     |
| `POST`                    | `/api/agent/model-provider/{id}/test`                     | 供应商连通性诊断，返回 `success`、`elapsedMs`、`error` |
| `GET`                     | `/api/agent/model-provider/models`                        | 按 `providerId` 查询目录                       |
| `GET`                     | `/api/agent/model-provider/models/options?capability=...` | 按能力获取可选目录模型                               |
| `GET`                     | `/api/agent/model-provider/{id}/models/discover`          | 读取供应商模型候选                                 |
| `POST`                    | `/api/agent/model-provider/models`                        | 创建一个目录项                                   |
| `POST`                    | `/api/agent/model-provider/models/batch`                  | 事务批量创建目录项                                 |
| `PUT` / `DELETE`          | `/api/agent/model-provider/models/{id}`                   | 修改或删除目录项                                  |
| `PUT`                     | `/api/agent/model-provider/models/{id}/status`            | 启停目录项                                     |

目录创建、修改和批量创建会统一校验：供应商存在且启用、模型名非空、至少存在一个能力、能力仅能取上述支持值。运行时还会校验目录状态、供应商状态及所需业务能力。

## 5. 业务配置映射

| 业务配置         | 保存字段                                  | 必需能力                  |
|--------------|---------------------------------------|-----------------------|
| Agent 模型     | `agent_definition.model_id`           | `CHAT` 或 `MULTIMODAL` |
| 知识库向量模型      | `knowledge_base.embedding_model_id`   | `EMBEDDING`           |
| 查询重写模型       | `retrievalConfig.queryRewriteModelId` | `CHAT` 或 `MULTIMODAL` |
| Rerank 模型    | `retrievalConfig.rerankModelId`       | `RERANK`              |
| AI 审查模型      | `reviewConfig.reviewModelId`          | `CHAT` 或 `MULTIMODAL` |
| Skill 路由向量模型 | 系统配置 `skill.routing.embeddingModelId` | `EMBEDDING`           |

评测运行快照保存模型名称、供应商名称、能力和非敏感调用配置，供排查评测差异；不展示目录内部 ID 和 API Key。

## 6. 前端提示约定

接口成功或失败的消息由全局请求响应拦截器统一展示。业务页面不得在接口调用成功后再次弹出同类成功消息，以免重复提示。仅保留无需请求即可判定的本地交互提示，例如“尚未选择模型能力”或“所选模型已经全部存在”。

新增页面文字必须同时维护 `aether-dashboard/src/locales/zh-CN.ts` 和 `en-US.ts`；后端返回文案维护
`api/src/main/resources/i18n/api_zh_CN.properties` 与 `api_en_US.properties`。

## 7. 运维与排障

1. 业务选择器没有可选模型：检查目录能力、目录状态、供应商状态以及权限。
2. 模型列表读取失败：确认供应商基础地址和 API Key；非百炼供应商需要实现 OpenAI 兼容的模型枚举接口。
3. 连通性诊断失败：检查网络出口、TLS、地址、认证和 5/10 秒超时。请求耗时可在供应商列表中查看。
4. Rerank 失败：确认目录能力为 `RERANK`，并确认供应商实际支持配置的 Rerank 端点和响应结构；能力正确不代表端点协议一定兼容。
5. 更换向量模型或向量维度后：应重新索引受影响的知识库和 Skill 路由索引，不能混用不同维度的历史向量。

