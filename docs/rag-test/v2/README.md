# Aether 企业级 RAG 评测语料（v2）

该目录模拟 SaaS 企业在客户成功、访问治理、数据保护、生产运维和 API 商业运营中的真实知识库。内容包含近义词、时限、例外条款、跨文档概念和相似数字，适合验证召回、重排、引用和拒答能力。

## 语料清单

| 文件 | 场景 | 建议检索难点 |
| --- | --- | --- |
| `rag-test-knowledge-base.md` | 客户成功、订阅、支持、事故沟通 | SLA 与服务计划、退款例外、账户恢复 |
| `01-identity-access-governance.md` | SSO、MFA、RBAC、临时授权 | 权限边界、角色职责、审计保留 |
| `02-data-protection-and-knowledge-governance.md` | 数据分类、删除、导出、泄露 | 人工与 AI 审查差异、保留例外 |
| `03-production-change-and-incident-runbook.md` | 变更、事故、复盘 | P1/P2 分级、恢复条件、冻结期 |
| `04-api-integration-and-billing-operations.md` | API、Webhook、计量和账单争议 | 幂等、429、计费例外、密钥轮换 |
| `05-enterprise-contract-and-service-management.md` | 合同、服务信用、客户成功 | SLA 例外、续费节点、升级边界 |
| `06-ai-governance-and-model-risk-control.md` | AI 风险、提示安全、评测治理 | 人工监督、拒答、模型变更回归 |

## 企业文件包

`enterprise-assets/` 提供可直接上传的原生企业文件，用于同时验证 Markdown、Word、PDF 与 Excel 的解析、分块和检索质量：

| 文件 | 格式 | 企业场景 | 适合验证的能力 |
| --- | --- | --- | --- |
| `01-enterprise-customer-operations-manual.docx` | DOCX | 支持分级、事故沟通、服务信用、退出服务 | 表格、分级流程、时限与例外条款 |
| `02-identity-data-ai-governance-standard.docx` | DOCX | 身份权限、知识库数据、AI 风险控制 | 角色边界、审查门槛、治理规则 |
| `03-ai-risk-and-model-governance-standard.pdf` | PDF | AI 治理受控发布标准 | 多级章节、受控元数据、提示安全 |
| `04-enterprise-service-operations-register.xlsx` | XLSX | 变更、风险、服务等级与运营指标台账 | 多工作表、公式、状态字段与结构化表格 |

建议先上传并审批 `enterprise-assets/` 中的四份文件，再上传 Markdown 语料。这样可以在同一知识库内验证不同格式的文本提取、段落/表格分块和跨文档召回。所有内容均为虚构测试资料，不含真实客户或生产凭据。

## 使用步骤

1. 将上表全部 Markdown 文件上传到同一个知识库，等待每份文件通过人工审查并完成索引。
2. 在“文档管理”复制每份上传文档的 **文档 ID**。请确认其状态为“已通过”和“已完成索引”。
3. 在 `rag-evaluation-import-template.json` 中替换以下占位符，保留双引号：

   - `REPLACE_WITH_CUSTOMER_OPERATIONS_DOCUMENT_ID`
   - `REPLACE_WITH_ACCESS_GOVERNANCE_DOCUMENT_ID`
   - `REPLACE_WITH_DATA_GOVERNANCE_DOCUMENT_ID`
   - `REPLACE_WITH_INCIDENT_RUNBOOK_DOCUMENT_ID`
   - `REPLACE_WITH_API_BILLING_DOCUMENT_ID`
   - `REPLACE_WITH_CONTRACT_SERVICE_DOCUMENT_ID`
   - `REPLACE_WITH_AI_GOVERNANCE_DOCUMENT_ID`

4. 在“检索评测 → 数据集与标注”导入 JSON，先执行预校验，再确认写入。
5. 发布评测集版本并运行评测。建议将首个稳定运行设为基线，再调整召回参数、Chunk 策略或 Rerank 后进行对比。

## 导入失败排查

`DOCUMENT_UNAVAILABLE` 表示 JSON 中的 `documentId` 不属于当前环境可用文档。最常见原因是：仍使用占位符、复制了其他环境的 ID、目标文档尚未审批/索引完成，或文档已删除。不要复用示例中的旧 ID；当前模板不包含任何可直接导入的固定 ID。

模板以 `DOCUMENT` 粒度构建，用于稳定验证跨文档召回。完成首轮验证后，可在工作台中将高价值问题补充为 `SECTION` 或 `CHUNK` 标签，以测试精确引用。
