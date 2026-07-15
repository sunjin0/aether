param(
    [Parameter(Mandatory = $true)]
    [string]$MySqlDsn,

    [Parameter(Mandatory = $true)]
    [string]$PostgresDsn
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$template = Join-Path $PSScriptRoot 'mysql-to-postgres.load.template'
$temporaryLoadFile = Join-Path $env:TEMP 'aether-mysql-to-postgres.load'

try {
    $content = Get-Content -Raw -Encoding UTF8 $template
    $content = $content.Replace('{{MYSQL_DSN}}', $MySqlDsn).Replace('{{POSTGRES_DSN}}', $PostgresDsn)
    [System.IO.File]::WriteAllText($temporaryLoadFile, $content, [System.Text.UTF8Encoding]::new($false))

    docker run --rm -v "${env:TEMP}:/work" dimitri/pgloader:latest pgloader /work/aether-mysql-to-postgres.load
    if ($LASTEXITCODE -ne 0) {
        throw "pgloader exited with code $LASTEXITCODE"
    }
}
finally {
    Remove-Item -LiteralPath $temporaryLoadFile -Force -ErrorAction SilentlyContinue
}
