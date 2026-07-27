# RAG 测试问题与标准答案

## 使用说明

该资料包用于测试文档解析、向量召回、重排、跨文档综合、冲突信息判断和无依据拒答。建议要求回答附带来源文件名。

## 测试问题

| 编号 | 问题 | 标准答案要点 | 应命中文件 | 测试点 |
| --- | --- | --- | --- | --- |
| Q01 | P0 工单的首次响应目标是多少？ | 15 分钟。 | 01-product-handbook.pdf, 04-billing-and-sla.xlsx | 单事实、PDF/Excel一致性 |
| Q02 | Professional 套餐最多支持多少坐席和多少队列？ | 300 名坐席、20 个队列。 | 01-product-handbook.md, 04-billing-and-sla.xlsx | 表格抽取 |
| Q03 | v3.2 的知识命中率统计口径是什么？ | 被用户采纳的答案数 / 已触发知识推荐的工单数。 | 01-product-handbook.md, 05-release-notes.xlsx | 版本优先级 |
| Q04 | 旧的知识命中率口径还能作为当前答案吗？ | 不能。旧口径“推荐答案展示次数 / 工单总数”已在 v3.2.0 废弃。 | 01-product-handbook.md, 05-release-notes.xlsx | 冲突信息识别 |
| Q05 | 审计员是否能查看内部处理备注？ | 当前 2026.07/v3.2 规则下可以查看，但不能修改工单状态或备注。 | 03-permission-policy.docx, 05-release-notes.xlsx | 新旧规则覆盖 |
| Q06 | 部署手册中的“升级”和 Flow Desk 的“自动升级”是不是同一件事？ | 不是。部署手册的升级是软件版本升级；Flow Desk 自动升级是 SLA 层级升级。 | 01-product-handbook.md, 02-deployment-guide.docx, 06-faq-troubleshooting.pdf | 相似术语区分 |
| Q07 | API 服务默认端口是多少？ | 当前默认端口是 8090；8080 是已废弃旧规则。 | 02-deployment-guide.docx, 05-release-notes.xlsx | 废弃规则处理 |
| Q08 | P0 工单暂停 SLA 超过 4 小时需要谁确认？ | 主管和审计员双人确认。 | 03-permission-policy.docx, 06-faq-troubleshooting.pdf | 跨文档一致性 |
| Q09 | E-AUTH-403 应该排查什么？ | 排查角色、队列成员关系和字段级脱敏权限。 | 06-faq-troubleshooting.pdf, 03-permission-policy.docx | 故障排查 |
| Q10 | 星澜平台是否支持微信支付开票？ | 文档没有依据说明支持，应拒答或说明未找到相关信息。 | 06-faq-troubleshooting.pdf | 无答案拒答 |
| Q11 | Standard 套餐支持哪些字段脱敏？ | 仅支持手机号脱敏。 | 03-permission-policy.docx, 04-billing-and-sla.xlsx | 多源校验 |
| Q12 | 如果 v3.2 升级失败，回滚到 v3.1.7 前要注意什么？ | 必须先禁用新口径的知识命中率任务，否则旧版本报表会出现字段缺失。 | 02-deployment-guide.docx | 运维步骤抽取 |

## 评分建议

- 准确性：答案是否与标准答案一致。
- 来源性：是否引用了正确文件。
- 时效性：是否优先采用当前版本规则。
- 拒答：无依据问题是否避免编造。
- 格式解析：是否能从 PDF、DOCX、XLSX 中正确抽取事实。