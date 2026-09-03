param(
    [string]$AdminUrl = 'http://localhost:8080',
    [string]$PostgresContainer = 'aether-postgres',
    [string]$DatabaseUser = 'sunjin',
    [string]$DatabaseName = 'aether'
)

$ErrorActionPreference = 'Stop'
$testUserId = 'refresh-e2e-' + [Guid]::NewGuid().ToString('N').Substring(0, 16)
$now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

function ConvertTo-Base64Url([byte[]]$bytes) {
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-Jwt([hashtable]$claims) {
    $header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
    $payload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes(($claims | ConvertTo-Json -Compress)))
    $unsigned = "$header.$payload"
    $hmac = [Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes('1sa(s}>s.@jj,asj.!hg5454'))
    try { return "$unsigned.$(ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsigned))))" }
    finally { $hmac.Dispose() }
}

function Protect-Token([string]$token) {
    $aes = [Security.Cryptography.Aes]::Create()
    $aes.Mode = [Security.Cryptography.CipherMode]::CBC
    $aes.Padding = [Security.Cryptography.PaddingMode]::PKCS7
    $aes.Key = [Text.Encoding]::UTF8.GetBytes('1k_)(+*/@!abc.ef')
    $aes.IV = [Text.Encoding]::UTF8.GetBytes('0123456789abcdef')
    try {
        $encryptor = $aes.CreateEncryptor()
        try { return [Convert]::ToBase64String($encryptor.TransformFinalBlock([Text.Encoding]::UTF8.GetBytes($token), 0, [Text.Encoding]::UTF8.GetByteCount($token))) }
        finally { $encryptor.Dispose() }
    } finally { $aes.Dispose() }
}

function Invoke-Sql([string]$sql) {
    & docker exec $PostgresContainer psql -v ON_ERROR_STOP=1 -U $DatabaseUser -d $DatabaseName -c $sql | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL command failed.' }
}

try {
    $accessToken = Protect-Token (New-Jwt @{ userId = $testUserId; role = 'E2E'; tokenType = 'access'; exp = $now + 600 })
    $refreshToken = Protect-Token (New-Jwt @{ userId = $testUserId; role = 'E2E'; tokenType = 'refresh'; exp = $now + 600 })
    $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    Invoke-Sql "INSERT INTO sys_user (id, username, password, type, state, deleted, created_at, updated_at, sort_num) VALUES ('$testUserId', '$testUserId', 'e2e', 'E2E', 0, false, $timestamp, $timestamp, 1);"
    Invoke-Sql "INSERT INTO sys_token (id, user_id, token, refresh_token, state, deleted, created_at, updated_at, sort_num) VALUES ('$testUserId', '$testUserId', '$accessToken', '$refreshToken', 1, false, $timestamp, $timestamp, 1);"

    $refreshResponse = Invoke-RestMethod -Method Post -Uri "$AdminUrl/api/sys/refresh" -ContentType 'application/json' -Body (@{ refreshToken = $refreshToken } | ConvertTo-Json -Compress)
    if ($refreshResponse.code -ne 200 -or [string]::IsNullOrWhiteSpace($refreshResponse.data.token) -or [string]::IsNullOrWhiteSpace($refreshResponse.data.refreshToken)) {
        throw 'Refresh endpoint did not issue a replacement token pair.'
    }
    if ($refreshResponse.data.token -eq $accessToken -or $refreshResponse.data.refreshToken -eq $refreshToken) {
        throw 'Refresh endpoint did not rotate the token pair.'
    }

    try {
        Invoke-WebRequest -Uri "$AdminUrl/api/sys/info" -Headers @{ Authorization = "Bearer $($refreshResponse.data.token)" } -UseBasicParsing | Out-Null
        throw 'The isolated user unexpectedly has application permissions.'
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -ne 403) { throw }
    }

    Write-Host 'PASS: refresh endpoint rotated tokens and the new access token passed authentication into the permission layer.'
} finally {
    try { Invoke-Sql "DELETE FROM sys_token WHERE user_id = '$testUserId';" } catch { Write-Warning 'Unable to clean up the temporary token row.' }
    try { Invoke-Sql "DELETE FROM sys_user WHERE id = '$testUserId';" } catch { Write-Warning 'Unable to clean up the temporary user row.' }
}
