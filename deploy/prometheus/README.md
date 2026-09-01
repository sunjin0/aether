# Prometheus 告警规则

以 `prometheus.yml.example` 为模板配置 scrape target，并将 `aether-alerts.yml` 放到 Prometheus 的 `rule_files`，再按部署环境配置 Alertmanager receiver。Prometheus 原生不会展开 `${...}`，部署脚本需要在挂载前完成环境变量渲染。

规则只依赖应用暴露的 Prometheus 指标；若业务自定义死信计数尚未注册，可先禁用对应规则，待运维指标注册后启用。
