# Queue 성능 테스트에서 생성한 데이터만 manifest 기준으로 확인·정리합니다.
# 기본 실행은 삭제하지 않습니다. 결과를 확인한 뒤 -Apply를 붙여 실행합니다.
# Windows: powershell.exe -ExecutionPolicy Bypass -File "./scripts/load-test/queue/03-cleanup-performance.ps1" -ManifestPath "<manifest 경로>" -Apply
# macOS/Linux: pwsh -NoProfile -File "./scripts/load-test/queue/03-cleanup-performance.ps1" -ManifestPath "<manifest 경로>" -Apply

param(
    # dry-run에서 지정하지 않으면 가장 최근 Queue 성능 테스트 manifest를 사용한다. -Apply에서는 반드시 지정한다.
    [string]$ManifestPath = "",
    [switch]$Apply,
    [string]$MySqlService = "mysql-db",
    [string]$RedisService = "redis"
)

$ErrorActionPreference = "Stop"
$script:RepoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$script:ComposeFile = Join-Path $script:RepoRoot "docker-compose.yml"

function Invoke-Compose {
    param([string[]]$Arguments, [string]$InputText)
    $composeArguments = @("compose", "--project-directory", $script:RepoRoot, "-f", $script:ComposeFile) + $Arguments
    if ($PSBoundParameters.ContainsKey("InputText")) { $output = $InputText | & docker @composeArguments 2>&1 }
    else { $output = & docker @composeArguments 2>&1 }
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose 명령 실행에 실패했습니다. $($output -join [Environment]::NewLine)" }
    $output
}

function Invoke-PerformanceMySql {
    param([string]$Sql, [switch]$Scalar, [switch]$NoHeaders)
    $mysql = if ($Scalar -or $NoHeaders) { 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot -Dreseat --batch --raw --skip-column-names' }
    else { 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot -Dreseat --batch --raw' }
    $previousOutputEncoding = $OutputEncoding
    try {
        $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
        $output = Invoke-Compose -Arguments @("exec", "-T", $MySqlService, "sh", "-lc", $mysql) -InputText $Sql
    } finally { $OutputEncoding = $previousOutputEncoding }
    if ($Scalar) { return ($output | Select-Object -First 1).ToString().Trim() }
    $output
}

function Get-ComposeSecret {
    param([string]$Name)
    $variable = Get-Item -Path "Env:$Name" -ErrorAction SilentlyContinue
    if ($variable -and -not [string]::IsNullOrWhiteSpace([string]$variable.Value)) { return $variable.Value }
    $line = Get-Content -LiteralPath (Join-Path $script:RepoRoot '.env') | Where-Object { $_ -match "^\s*$Name\s*=" } | Select-Object -First 1
    if (-not $line) { throw "$Name 값을 찾지 못했습니다. 배포 서버의 환경 변수 또는 .env를 확인해주세요." }
    (($line -split "=", 2)[1].Trim().Trim('"').Trim("'"))
}

function Invoke-RedisKeyCommand {
    param([string]$Command, [string[]]$Keys)
    $affectedCount = 0
    for ($offset = 0; $offset -lt $Keys.Count; $offset += 100) {
        $lastIndex = [Math]::Min($offset + 99, $Keys.Count - 1)
        $batch = @($Keys[$offset..$lastIndex])
        $output = Invoke-Compose -Arguments (@('exec', '-T', '-e', "REDISCLI_AUTH=$redisPassword", $RedisService, 'redis-cli', $Command) + $batch)
        $affectedCount += [int](($output | Select-Object -Last 1).ToString().Trim())
    }
    $affectedCount
}

function Resolve-ManifestPath {
    if (-not [string]::IsNullOrWhiteSpace($ManifestPath)) { return $ManifestPath }
    if ($Apply) { throw '-Apply를 사용할 때는 dry-run에서 확인한 -ManifestPath를 명시해주세요.' }
    $runsPath = Join-Path $script:RepoRoot 'build/k6/runs'
    if (-not (Test-Path -LiteralPath $runsPath)) { throw 'Queue 성능 테스트 manifest를 찾지 못했습니다. -ManifestPath를 지정해주세요.' }
    $latest = Get-ChildItem -LiteralPath $runsPath -Filter 'manifest.json' -Recurse -File |
        Sort-Object LastWriteTime -Descending |
        Where-Object { (Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json).testType -eq 'queue-performance' } |
        Select-Object -First 1
    if (-not $latest) { throw 'Queue 성능 테스트 manifest를 찾지 못했습니다. -ManifestPath를 지정해주세요.' }
    $latest.FullName
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker CLI를 찾을 수 없습니다.' }
if (-not (Test-Path -LiteralPath $script:ComposeFile)) { throw 'docker-compose.yml을 찾을 수 없습니다. re-seat 저장소에서 실행해주세요.' }
$ManifestPath = Resolve-ManifestPath
if (-not (Test-Path -LiteralPath $ManifestPath)) { throw "manifest 파일을 찾을 수 없습니다. 경로: $ManifestPath" }

$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
if ($manifest.testType -ne 'queue-performance' -or [string]::IsNullOrWhiteSpace([string]$manifest.runId)) { throw 'Queue 성능 테스트에서 만든 manifest가 아닙니다.' }
$gameId = [long]$manifest.testGameId
$runId = [string]$manifest.runId
if ($gameId -le 0) { throw '정리에 필요한 테스트 경기 ID가 manifest에 없습니다.' }
if ($runId -notmatch '^[a-z0-9-]{3,60}$') { throw 'manifest의 RunId 형식이 올바르지 않습니다.' }

$escapedRunId = [regex]::Escape($runId)
$expectedUserEmails = @($manifest.expectedUserEmails | ForEach-Object { [string]$_ } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
$recordedUserEmails = @($manifest.users | ForEach-Object { [string]$_.email } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
$performanceUserEmails = @($expectedUserEmails + $recordedUserEmails | Select-Object -Unique)
foreach ($email in $performanceUserEmails) {
    if ($email -notmatch "^queue-user-$escapedRunId-\d{4}@reseat\.local$") { throw "manifest의 테스트 사용자 이메일 형식이 올바르지 않습니다. email=$email" }
}

$adminUserEmail = if (-not [string]::IsNullOrWhiteSpace([string]$manifest.expectedAdminEmail)) {
    [string]$manifest.expectedAdminEmail
} else {
    [string]$manifest.adminUser.email
}
if (-not [string]::IsNullOrWhiteSpace($adminUserEmail) -and $adminUserEmail -ne "queue-admin-$RunId@reseat.local") {
    throw 'manifest의 임시 관리자 이메일 형식이 올바르지 않습니다.'
}

$cleanupUserEmails = @($performanceUserEmails + $adminUserEmail | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)
$emailList = if ($cleanupUserEmails.Count -gt 0) {
    ($cleanupUserEmails | ForEach-Object { "'$($_.Replace("'", "''"))'" }) -join ','
} else { 'NULL' }
$resolvedUsers = @(Invoke-PerformanceMySql -NoHeaders -Sql "SELECT id, email FROM users WHERE email IN ($emailList) ORDER BY id;" | ForEach-Object {
    $columns = $_ -split "`t", 2
    [PSCustomObject]@{ userId = [long]$columns[0]; email = $columns[1] }
})
$userIds = @($resolvedUsers | Where-Object { $_.email -in $performanceUserEmails } | ForEach-Object { [long]$_.userId })
$cleanupUserIds = @($resolvedUsers | ForEach-Object { [long]$_.userId })
$userIdList = if ($userIds.Count -gt 0) { $userIds -join ',' } else { 'NULL' }
$cleanupUserIdList = if ($cleanupUserIds.Count -gt 0) { $cleanupUserIds -join ',' } else { 'NULL' }
$unexpectedUserFilter = if ($userIds.Count -gt 0) { "WHERE user_id NOT IN ($userIdList)" } else { '' }

# manifest의 경기·사용자 관계를 DB에서 다시 확인한다.
$testGameCount = [int](Invoke-PerformanceMySql -Scalar -Sql "SELECT COUNT(*) FROM games WHERE id = $gameId AND title LIKE '[Queue 성능테스트 $runId]%';")
if ($testGameCount -ne 1) { throw 'manifest의 경기 ID가 이 실행의 성능 테스트 경기인지 확인하지 못했습니다. 삭제하지 않습니다.' }
$unexpectedUsers = [int](Invoke-PerformanceMySql -Scalar -Sql @"
SELECT COUNT(*) FROM (
    SELECT qeh.user_id FROM queue_entry_histories qeh WHERE qeh.game_id = $gameId
    UNION SELECT at.user_id FROM admission_tokens at WHERE at.game_id = $gameId
    UNION SELECT r.user_id FROM reservations r WHERE r.game_id = $gameId
    UNION SELECT o.user_id FROM orders o JOIN reservations r ON r.id = o.reservation_id WHERE r.game_id = $gameId
    UNION SELECT p.user_id FROM payments p JOIN orders o ON o.id = p.order_id JOIN reservations r ON r.id = o.reservation_id WHERE r.game_id = $gameId
    UNION SELECT t.user_id FROM tickets t WHERE t.game_id = $gameId
) related_users $unexpectedUserFilter;
"@)
if ($unexpectedUsers -ne 0) { throw "다른 사용자와 연결된 테스트 경기 데이터가 ${unexpectedUsers}건 있습니다. 자동 정리를 중단합니다." }

$summary = Invoke-PerformanceMySql -Sql @"
SELECT 'queue_entry_histories' AS target, COUNT(*) AS count FROM queue_entry_histories WHERE game_id = $gameId AND user_id IN ($userIdList)
UNION ALL SELECT 'admission_tokens', COUNT(*) FROM admission_tokens WHERE game_id = $gameId AND user_id IN ($userIdList)
UNION ALL SELECT 'reservations', COUNT(*) FROM reservations WHERE game_id = $gameId AND user_id IN ($userIdList)
UNION ALL SELECT 'orders', COUNT(*) FROM orders o JOIN reservations r ON r.id = o.reservation_id WHERE r.game_id = $gameId AND o.user_id IN ($userIdList)
UNION ALL SELECT 'payments', COUNT(*) FROM payments p JOIN orders o ON o.id = p.order_id JOIN reservations r ON r.id = o.reservation_id WHERE r.game_id = $gameId AND p.user_id IN ($userIdList)
UNION ALL SELECT 'tickets', COUNT(*) FROM tickets WHERE game_id = $gameId AND user_id IN ($userIdList)
UNION ALL SELECT 'payment_cancels', COUNT(*) FROM payment_cancels pc JOIN payments p ON p.id = pc.payment_id JOIN orders o ON o.id = p.order_id JOIN reservations r ON r.id = o.reservation_id WHERE r.game_id = $gameId AND r.user_id IN ($userIdList)
UNION ALL SELECT 'payment_recovery_tasks', COUNT(*) FROM payment_recovery_tasks prt JOIN payments p ON p.id = prt.payment_id JOIN orders o ON o.id = p.order_id JOIN reservations r ON r.id = o.reservation_id WHERE r.game_id = $gameId AND r.user_id IN ($userIdList)
UNION ALL SELECT 'order_items', COUNT(*) FROM order_items oi JOIN orders o ON o.id = oi.order_id JOIN reservations r ON r.id = o.reservation_id WHERE r.game_id = $gameId AND r.user_id IN ($userIdList)
UNION ALL SELECT 'reservation_seats', COUNT(*) FROM reservation_seats rs JOIN reservations r ON r.id = rs.reservation_id WHERE r.game_id = $gameId AND r.user_id IN ($userIdList)
UNION ALL SELECT 'game_seats', COUNT(*) FROM game_seats WHERE game_id = $gameId
UNION ALL SELECT 'users', COUNT(*) FROM users WHERE id IN ($cleanupUserIdList) AND email IN ($emailList);
"@
$redisPassword = Get-ComposeSecret -Name 'REDIS_PASSWORD'
$redisKeys = @("queue:waiting:game:$gameId", "lock:queue:admit:$gameId")
foreach ($userId in $userIds) {
    $redisKeys += "queue:entry:game:$gameId:user:$userId"
    $redisKeys += "queue:entry:rejection:game:$gameId:user:$userId"
    $redisKeys += "queue:entry:request:latest:game:$gameId:user:$userId"
}
$redisKeyCount = Invoke-RedisKeyCommand -Command 'EXISTS' -Keys $redisKeys
Write-Host "정리 대상 확인: $ManifestPath"
$summary | ForEach-Object { Write-Host $_ }
Write-Host "Queue Redis Key: ${redisKeyCount}건"
if (-not $Apply) { Write-Host '삭제하지 않았습니다. 측정 결과와 Kafka 처리 완료를 확인한 뒤 -Apply를 붙여 다시 실행해주세요.'; return }

# 테스트 데이터 정리
# FK 순서와 manifest의 실행 범위를 함께 적용해 다른 실행 데이터는 삭제하지 않는다.
Write-Host '[1/2] 테스트 경기의 Queue Redis 키만 삭제합니다.'
$deletedRedisKeyCount = Invoke-RedisKeyCommand -Command 'DEL' -Keys $redisKeys
Write-Host "삭제한 Queue Redis Key: ${deletedRedisKeyCount}건"

Write-Host '[2/2] manifest에 기록된 DB 데이터만 삭제합니다.'
Invoke-PerformanceMySql -Sql @"
START TRANSACTION;
DELETE prt FROM payment_recovery_tasks prt JOIN payments p ON p.id = prt.payment_id JOIN orders o ON o.id = p.order_id JOIN reservations r ON r.id = o.reservation_id WHERE r.game_id = $gameId AND r.user_id IN ($userIdList);
DELETE pc FROM payment_cancels pc JOIN payments p ON p.id = pc.payment_id JOIN orders o ON o.id = p.order_id JOIN reservations r ON r.id = o.reservation_id WHERE r.game_id = $gameId AND r.user_id IN ($userIdList);
DELETE t FROM tickets t WHERE t.game_id = $gameId AND t.user_id IN ($userIdList);
DELETE p FROM payments p JOIN orders o ON o.id = p.order_id JOIN reservations r ON r.id = o.reservation_id WHERE r.game_id = $gameId AND r.user_id IN ($userIdList);
DELETE oi FROM order_items oi JOIN orders o ON o.id = oi.order_id JOIN reservations r ON r.id = o.reservation_id WHERE r.game_id = $gameId AND r.user_id IN ($userIdList);
DELETE o FROM orders o JOIN reservations r ON r.id = o.reservation_id WHERE r.game_id = $gameId AND r.user_id IN ($userIdList);
DELETE rs FROM reservation_seats rs JOIN reservations r ON r.id = rs.reservation_id WHERE r.game_id = $gameId AND r.user_id IN ($userIdList);
DELETE FROM reservations WHERE game_id = $gameId AND user_id IN ($userIdList);
DELETE FROM admission_tokens WHERE game_id = $gameId AND user_id IN ($userIdList);
DELETE FROM queue_entry_histories WHERE game_id = $gameId AND user_id IN ($userIdList);
DELETE FROM game_seats WHERE game_id = $gameId;
DELETE FROM games WHERE id = $gameId AND title LIKE '[Queue 성능테스트 $runId]%';
DELETE FROM users WHERE id IN ($cleanupUserIdList) AND email IN ($emailList);
COMMIT;
"@ | Out-Null
$manifest.cleanup.appliedAt = (Get-Date).ToString('o')
[System.IO.File]::WriteAllText($ManifestPath, ($manifest | ConvertTo-Json -Depth 8), [System.Text.UTF8Encoding]::new($false))
Write-Host '정리가 완료됐습니다. manifest와 측정 결과는 보존했습니다.'
