$ErrorActionPreference = 'Stop'
$migrationRoot = Join-Path $PSScriptRoot '..\api\src\main\resources\db\migration\postgresql'
$files = Get-ChildItem -LiteralPath $migrationRoot -Filter 'V*__*.sql' -File
$numbers = @{}
foreach ($file in $files) {
    if ($file.Name -notmatch '^V([0-9]+)__[A-Za-z0-9][A-Za-z0-9_-]*\.sql$') {
        throw "Invalid Flyway migration filename: $($file.Name)"
    }
    $number = [int64]$Matches[1]
    if ($numbers.ContainsKey($number)) {
        throw "Duplicate Flyway migration version V${number}: $($numbers[$number]) and $($file.Name)"
    }
    $numbers[$number] = $file.Name
}
Write-Host "Flyway migration check passed: $($files.Count) migrations, versions V$(([int64]($numbers.Keys | Measure-Object -Maximum).Maximum))"
