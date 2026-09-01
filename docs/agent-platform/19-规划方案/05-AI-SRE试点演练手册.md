# AI SRE 试点演练手册

## 目的

在隔离测试租户验证官方 AI SRE Solution 从 Alertmanager 告警、诊断 Workflow、连接器只读查询到人工审批的闭环。演练不得使用生产凭据，也不得把真实告警中的 Token、Cookie 或业务敏感字段写入样例。

## 前置条件

1. 安装官方 `ai-sre` Solution，并确认 `diagnosisWorkflow.code`、`knowledgeBase.code` 和三类只读 Connector 均已解析到当前租户或公共范围内的有效资源。
2. 为测试租户配置 Prometheus、Grafana、Kubernetes 的 `credentialRef`；凭据只由 Secret Provider 提供，不能写入 Workflow 变量、Prompt 或审计正文。
3. 创建 `businessType=ai-sre-alert` 的 Webhook 触发器，保存一次性返回的 signing secret 到临时密钥存储。

## 演练步骤

1. 发送一条包含 `status=firing`、非空 `labels.alertname` 和 `alerts` 数组的签名 Alertmanager 请求到触发器地址 `/api/agent/workflow/webhook/{triggerId}`。
2. 使用事件 ID 重复发送同一请求，确认只产生一个 Workflow 实例，第二次请求返回幂等结果。
3. 在 Trace 页面检查告警、诊断 Agent、Prometheus/Grafana/Kubernetes 查询和人工审批节点处于同一 `traceId`，且凭据字段已掩码。
4. 在人工审批节点执行批准，确认修复前仍未发生任何写操作；拒绝时确认实例进入拒绝终态并保留审计记录。
5. 关闭任一 Connector 或诊断 Workflow 后再次发送新告警，确认新执行被门禁拒绝；历史 Trace、Artifact 和审计仍可读取。

## 验收证据

- Webhook 请求与响应（仅保留脱敏后的时间戳、事件 ID、HTTP 状态）。
- 幂等前后实例数量、执行账本和 Trace 截图或导出记录。
- 人工审批批准/拒绝记录及对应审计事件。
- Connector 停用后的拒绝响应；日志、Trace、Prompt 和数据库中均不得出现凭据明文。
- 演练结束后关闭测试租户 Solution 或回滚版本，并确认历史执行未被删除。

## 失败回滚

任一安全检查失败时立即停用 Webhook 触发器和 Solution，撤销测试凭据并轮换 signing secret；不得通过重试绕过租户、版本、权限或人工审批门禁。生产上线前必须用真实 Alertmanager、Collector 和 IdP 环境重复本手册并保留外部系统证据。
