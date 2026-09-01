$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$failures = New-Object System.Collections.Generic.List[string]

function Require-Text([string]$path, [string]$pattern, [string]$description) {
    $full = Join-Path $repo $path
    if (-not (Test-Path -LiteralPath $full)) { $failures.Add("missing file: $path"); return }
    $text = Get-Content -LiteralPath $full -Raw
    if ($text -notmatch $pattern) { $failures.Add("missing contract: $description ($path)") }
}

function Reject-Text([string]$path, [string]$pattern, [string]$description) {
    $full = Join-Path $repo $path
    if (-not (Test-Path -LiteralPath $full)) { $failures.Add("missing file: $path"); return }
    $text = Get-Content -LiteralPath $full -Raw
    if ($text -match $pattern) { $failures.Add("forbidden contract: $description ($path)") }
}

& (Join-Path $PSScriptRoot 'verify-flyway-migrations.ps1') | Out-Null
Require-Text 'common/src/main/java/com/aether/interceptor/GlobalFilter.java' 'traceparent' 'W3C trace propagation'
Require-Text 'common/src/main/java/com/aether/exception/GlobalException.java' 'private String sanitize' 'exception response sanitization'
Require-Text 'common/src/main/java/com/aether/exception/GlobalException.java' '系统内部错误' 'generic unknown error response'
Require-Text 'common/src/main/java/com/aether/interceptor/RateLimitFilter.java' 'failOpen' 'rate limiter backend failure policy'
Require-Text 'api/src/main/resources/application.yml' 'AETHER_RATE_LIMIT_REQUESTS_PER_WINDOW:0' 'rate limiting disabled by default'
Require-Text 'api/src/main/resources/application.yml' 'AETHER_SCIM_TENANT_ID' 'SCIM tenant binding configuration'
Require-Text 'api/src/main/resources/application.yml' 'AETHER_SAML_ENABLED:false' 'SAML disabled by default'
Require-Text 'api/src/main/resources/application.yml' 'AETHER_SAML_METADATA_DRIVEN:false' 'SAML metadata mode disabled by default'
Require-Text 'admin/src/main/resources/application.yml' 'AETHER_ADMIN_OTEL_ENABLED:false' 'Admin OTel disabled by default'
Require-Text 'admin/src/main/resources/application.yml' 'AETHER_ADMIN_OTEL_LOGS_ENABLED:false' 'Admin OTel logs disabled by default'
Require-Text 'api/src/main/java/com/aether/sys/config/SamlIdentityProperties.java' 'metadata-uri, sso-url and redirect-uri must use HTTPS' 'SAML HTTPS contract'
Require-Text 'api/src/main/resources/db/migration/postgresql/V166__solution_tenant_isolation.sql' 'aether_solution_installation.*tenant_id' 'solution installation tenant migration'
Require-Text 'api/src/main/resources/db/migration/postgresql/V167__solution_tenant_unique_key.sql' 'aether_solution_tenant_code_version_uk' 'tenant-scoped solution uniqueness'
Require-Text 'api/src/main/resources/db/migration/postgresql/V168__seed_ai_sre_solution.sql' '"alert-webhook"' 'official AI SRE solution seed'
Require-Text 'api/src/main/resources/db/migration/postgresql/V169__solution_global_unique_key.sql' 'aether_solution_global_code_version_uk' 'global solution uniqueness'
Require-Text 'api/src/main/resources/db/migration/postgresql/V170__mcp_connector_version.sql' 'ADD COLUMN IF NOT EXISTS version' 'connector version metadata'
Require-Text 'api/src/main/resources/db/migration/postgresql/V170__mcp_connector_version.sql' "SET version = '1.0.0'" 'connector version backfill'
Require-Text 'api/src/main/resources/db/migration/postgresql/V170__mcp_connector_version.sql' 'ALTER COLUMN version SET NOT NULL' 'connector version database invariant'
Require-Text 'api/src/main/java/com/aether/solution/entity/Solution.java' 'private String tenantId' 'solution tenant ownership'
Require-Text 'admin/src/main/java/com/aether/solution/controller/SolutionController.java' 'dependenciesAvailable' 'solution dependency gate'
Require-Text 'admin/src/main/java/com/aether/agent/controller/AgentMcpServerController.java' 'safeHealthMessage' 'connector health message sanitization'
Require-Text '.env.all.example' 'AETHER_SCIM_TENANT_ID=' 'full-stack SCIM tenant configuration'
Require-Text '.env.all.example' 'AETHER_SAML_ENABLED=false' 'full-stack SAML disabled default'
Require-Text '.env.all.example' 'AETHER_SAML_METADATA_DRIVEN=false' 'full-stack SAML metadata mode disabled default'
Require-Text '.env.all.example' 'AETHER_ADMIN_OTEL_ENABLED=false' 'full-stack Admin OTel disabled default'
Require-Text '.env.all.example' 'AETHER_ADMIN_OTEL_LOGS_ENABLED=false' 'full-stack Admin OTel logs disabled default'
Require-Text '.env.example' 'AETHER_SCIM_TENANT_ID=' 'standard SCIM tenant configuration'
Require-Text '.env.example' 'AETHER_SAML_ENABLED=false' 'standard SAML disabled default'
Require-Text '.env.example' 'AETHER_SAML_METADATA_DRIVEN=false' 'standard SAML metadata mode disabled default'
Require-Text '.env.example' 'AETHER_ADMIN_OTEL_ENABLED=false' 'standard Admin OTel disabled default'
Require-Text '.env.example' 'AETHER_ADMIN_OTEL_LOGS_ENABLED=false' 'standard Admin OTel logs disabled default'
Require-Text '.env.example' 'AETHER_SAML_SSO_URL=' 'standard SAML SSO endpoint configuration'
Require-Text '.env.all.example' 'AETHER_SAML_SSO_URL=' 'full-stack SAML SSO endpoint configuration'
Require-Text 'docker-compose.all.yml' 'AETHER_OTLP_TRACES_ENABLED' 'full-stack MCP OTel trace configuration'
Require-Text 'docker-compose.all.yml' 'AETHER_OTLP_LOGS_URL' 'full-stack MCP OTel log configuration'
Require-Text 'deploy/otel/README.md' 'AETHER_OTLP_TRACES_URL' 'OTel deployment configuration documentation'
Reject-Text 'common/src/main/java/com/aether/interceptor/GlobalFilter.java' 'getQueryString\(\)' 'request query must not enter global logs'
Reject-Text 'common/src/main/java/com/aether/interceptor/GlobalFilter.java' 'log\.(error|warn)\([^\r\n]*,\s*e\)' 'global filter must not log exception object'
Require-Text 'api/src/main/resources/application.yml' 'AETHER_WORKFLOW_EXECUTION_LEASE_MS:300000' 'execution lease configuration'
Require-Text 'biz/src/main/java/com/aether/workflow/runtime/WorkflowExecutionJobDispatcher.java' '\$\{aether\.workflow\.execution\.lease-ms:300000\}' 'configurable execution lease binding'
Require-Text 'admin/src/main/resources/application.yml' 'include: health,info,metrics' 'restricted actuator exposure'
Require-Text 'admin/src/main/resources/application.yml' 'shutdown: graceful' 'graceful application shutdown'
Require-Text 'admin/src/main/resources/application.yml' 'timeout-per-shutdown-phase' 'shutdown phase timeout'
Require-Text 'front/src/main/resources/application.yml' 'shutdown: graceful' 'front graceful application shutdown'
Require-Text '.env.example' 'SERVER_SHUTDOWN_TIMEOUT=30s' 'shutdown timeout environment contract'
Require-Text '.env.all.example' 'SERVER_SHUTDOWN_TIMEOUT=30s' 'full-stack shutdown timeout environment contract'
Require-Text 'docker-compose.yml' 'SERVER_SHUTDOWN_TIMEOUT' 'single-stack shutdown timeout propagation'
Require-Text 'docker-compose.all.yml' 'SERVER_SHUTDOWN_TIMEOUT' 'full-stack shutdown timeout propagation'
Require-Text 'docker-compose.yml' 'stop_grace_period: 40s' 'single-stack container drain grace period'
Require-Text 'docker-compose.all.yml' 'stop_grace_period: 40s' 'full-stack container drain grace period'
Require-Text 'docker-compose.yml' 'http://localhost:8080/actuator/health' 'single-stack application healthcheck'
Require-Text 'docker-compose.all.yml' 'http://localhost:8080/actuator/health' 'full-stack application healthcheck'
Require-Text 'docker-compose.all.yml' '127.0.0.1:8000/health' 'MCP application healthcheck'
Require-Text 'docker-compose.all.yml' 'container_name: aether-deep-agent' 'deep-agent service definition'
Require-Text 'docker-compose.all.yml' 'condition: service_healthy' 'health-gated application startup'
Require-Text 'api/src/main/resources/db/migration/postgresql/V161__agent_workflow_runtime_tenant_scope.sql' 'tenant_id' 'workflow runtime tenant migration'
Require-Text 'deploy/prometheus/aether-alerts.yml' 'AetherCallbackDeadLetters' 'callback dead-letter alert'
Require-Text 'deploy/prometheus/aether-alerts.yml' 'aether_workflow_execution_dead_letters' 'execution dead-letter alert metric'

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}
Write-Host 'Resilience contract check passed'
