$ErrorActionPreference = 'Stop'

function Assert-HttpOk([string]$Url) {
    $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 15
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
        throw "$Url returned HTTP $($response.StatusCode)"
    }
    Write-Host "PASS HTTP $Url ($($response.StatusCode))"
}

Assert-HttpOk 'http://localhost:13133/'
Assert-HttpOk 'http://localhost:9090/-/ready'
Assert-HttpOk 'http://localhost:3000/api/health'
Assert-HttpOk 'http://localhost:8081/realms/aether-local/.well-known/openid-configuration'
Assert-HttpOk 'http://localhost:8081/realms/aether-local/protocol/saml/descriptor'

$grafanaQuery = Invoke-RestMethod -Headers @{ Authorization = 'Bearer local-smoke' } `
    -Uri 'http://localhost:3000/api/datasources/uid/prom-main/resources/api/v1/query?query=up' -TimeoutSec 15
if ($grafanaQuery.status -ne 'success' -or $grafanaQuery.data.resultType -ne 'vector') {
    throw 'Grafana Prometheus datasource proxy did not return a successful vector query'
}
Write-Host 'PASS Grafana Prometheus datasource proxy query succeeded'

$targets = Invoke-RestMethod -Uri 'http://localhost:9090/api/v1/targets' -TimeoutSec 15
$collector = @($targets.data.activeTargets | Where-Object { $_.labels.job -eq 'otel-collector' })
if ($collector.Count -eq 0 -or ($collector | Where-Object health -ne 'up').Count -gt 0) {
    throw 'Prometheus does not report the OTel Collector target as up'
}
Write-Host 'PASS Prometheus target otel-collector is up'

$deployment = kubectl get deployment connector-sample -n aether-connector-test -o json | ConvertFrom-Json
if ($deployment.status.readyReplicas -ne 1 -or $deployment.status.availableReplicas -ne 1) {
    throw 'Kubernetes connector-sample deployment is not ready'
}
Write-Host 'PASS Kubernetes connector-sample is ready'

Write-Host 'Local platform smoke verification passed.'
