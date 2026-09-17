# reservation 성능 테스트에서 생성한 데이터를 manifest 기준으로 확인·정리합니다.
# 기본 실행은 삭제하지 않습니다. 결과를 확인한 뒤 -Apply를 붙여 실행합니다.
# Windows: pwsh.exe -NoProfile -File "./scripts/load-test/reservation/03-cleanup-performance.ps1" -ManifestPath "<manifest 경로>" -Apply
# macOS/Linux: pwsh -NoProfile -File "./scripts/load-test/reservation/03-cleanup-performance.ps1" -ManifestPath "<manifest 경로>" -Apply

param(
    [string]$ManifestPath = "",
    [switch]$Apply,
    [string]$MySqlService = "mysql-db",
    [string]$RedisService = "redis"
)

$ErrorActionPreference = "Stop"
$script:RepoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$script:ComposeFile = Join-Path $script:RepoRoot "docker-compose.yml"
$script:ExpectedTestType = 'reservation-performance'

. (Join-Path $PSScriptRoot 'common-performance.ps1')

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker CLI를 찾을 수 없습니다.' }
if (-not (Test-Path -LiteralPath $script:ComposeFile)) { throw 'docker-compose.yml을 찾을 수 없습니다.' }
$ManifestPath = Resolve-ManifestPath
if (-not (Test-Path -LiteralPath $ManifestPath)) { throw "manifest 파일을 찾을 수 없습니다. 경로: $ManifestPath" }
$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
if ($manifest.testType -ne 'reservation-performance' -or [string]::IsNullOrWhiteSpace([string]$manifest.runId)) { throw 'reservation 성능 테스트에서 만든 manifest가 아닙니다.' }

$gameId = [long]$manifest.testGameId
$runId = [string]$manifest.runId
if ($gameId -le 0) { throw '정리에 필요한 테스트 경기 ID가 manifest에 없습니다.' }
if ($runId -notmatch '^[a-z0-9-]{3,60}$') { throw 'manifest의 RunId 형식이 올바르지 않습니다.' }
$gameSeatIds = @($manifest.gameSeatIds | ForEach-Object { [long]$_ })

$escapedRunId = [regex]::Escape($runId)
$expectedUserEmails = @($manifest.expectedUserEmails | ForEach-Object { [string]$_ } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
$recordedUserEmails = @($manifest.users | ForEach-Object { [string]$_.email } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
$performanceUserEmails = @($expectedUserEmails + $recordedUserEmails | Select-Object -Unique)
foreach ($email in $performanceUserEmails) {
    if ($email -notmatch "^reservation-user-$escapedRunId-\d{4}@reseat\.local$") { throw "manifest의 테스트 사용자 이메일 형식이 올바르지 않습니다. email=$email" }
}
$adminUserEmail = if (-not [string]::IsNullOrWhiteSpace([string]$manifest.expectedAdminEmail)) { [string]$manifest.expectedAdminEmail } else { [string]$manifest.adminUser.email }
if (-not [string]::IsNullOrWhiteSpace($adminUserEmail) -and $adminUserEmail -ne "reservation-admin-$runId@reseat.local") {
    throw 'manifest의 임시 관리자 이메일 형식이 올바르지 않습니다.'
}

$cleanupUserEmails = @($performanceUserEmails + $adminUserEmail | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)
$emailList = if ($cleanupUserEmails.Count -gt 0) { ($cleanupUserEmails | ForEach-Object { "'$($_.Replace("'", "''"))'" }) -join ',' } else { 'NULL' }
$resolvedUsers = @(Invoke-PerformanceMySql -NoHeaders -Sql "SELECT id, email FROM users WHERE email IN ($emailList) ORDER BY id;" | ForEach-Object {
    $columns = $_ -split "`t", 2
    [PSCustomObject]@{ userId = [long]$columns[0]; email = $columns[1] }
})
$userIds = @($resolvedUsers | Where-Object { $_.email -in $performanceUserEmails } | ForEach-Object { [long]$_.userId })
$cleanupUserIds = @($resolvedUsers | ForEach-Object { [long]$_.userId })
$userIdList = if ($userIds.Count -gt 0) { $userIds -join ',' } else { 'NULL' }
$cleanupUserIdList = if ($cleanupUserIds.Count -gt 0) { $cleanupUserIds -join ',' } else { 'NULL' }
$unexpectedUserFilter = if ($userIds.Count -gt 0) { "WHERE user_id NOT IN ($userIdList)" } else { '' }

$testGameCount = [int](Invoke-PerformanceMySql -Scalar -Sql "SELECT COUNT(*) FROM games WHERE id = $gameId AND title LIKE '[Reservation 성능테스트 $runId]%';")
if ($testGameCount -ne 1) { throw 'manifest의 경기 ID가 이 실행의 성능 테스트 경기인지 확인하지 못했습니다. 삭제하지 않습니다.' }

# manifest.gameSeatIds가 실제로 이 경기(gameId) 소속인지 재확인한다.
if ($gameSeatIds.Count -gt 0) {
    $gameSeatIdList = $gameSeatIds -join ','
    $actualSeatCount = int FROM game_seats WHERE game_id = $gameId AND id IN ($gameSeatIdList);")
    if ($actualSeatCount -ne $gameSeatIds.Count) {
        throw "manifest의 gameSeatIds 중 일부가 이 경기($gameId) 소속이 아닙니다. 삭제하지 않습니다."
    }
}

$unexpectedUsers = [int](Invoke-PerformanceMySql -Scalar -Sql @"
SELECT COUNT(*) FROM (
    SELECT r.user_id FROM reservations r WHERE r.game_id = $gameId
    UNION SELECT at.user_id FROM admission_tokens at WHERE at.game_id = $gameId
) related_users $unexpectedUserFilter;
"@)
if ($unexpectedUsers -ne 0) { throw "다른 사용자와 연결된 테스트 경기 데이터가 ${unexpectedUsers}건 있습니다. 자동 정리를 중단합니다." }

# 요약 쿼리와 실제 삭제 쿼리가 같은 테이블 목록을 참조하도록 유지한다
# (#502 리뷰에서 지적된 "요약 ≠ 삭제 대상" 결함 재발 방지).
$summary = Invoke-PerformanceMySql -Sql @"
SELECT 'reservation_seats' AS target, COUNT(*) AS count FROM reservation_seats rs JOIN reservations r ON r.id = rs.reservation_id WHERE r.game_id = $gameId AND r.user_id IN ($userIdList)
UNION ALL SELECT 'reservations', COUNT(*) FROM reservations WHERE game_id = $gameId AND user_id IN ($userIdList)
UNION ALL SELECT 'admission_tokens', COUNT(*) FROM admission_tokens WHERE game_id = $gameId AND user_id IN ($userIdList)
UNION ALL SELECT 'game_seats', COUNT(*) FROM game_seats WHERE game_id = $gameId
UNION ALL SELECT 'games', COUNT(*) FROM games WHERE id = $gameId AND title LIKE '[Reservation 성능테스트 $runId]%'
UNION ALL SELECT 'users', COUNT(*) FROM users WHERE id IN ($cleanupUserIdList) AND email IN ($emailList);
"@

$redisPassword = Get-ComposeSecret -Name 'REDIS_PASSWORD'
$seatLockKeys = @($gameSeatIds | ForEach-Object { "lock:game-seat:$_" })
$userGameLockKeys = @($userIds | ForEach-Object { "lock:seat-hold:user:${_}:game:$gameId" })
$redisKeys = $seatLockKeys + $userGameLockKeys
$redisKeyCount = Invoke-RedisKeyCommand -Command 'EXISTS' -Keys $redisKeys

Write-Host "정리 대상 확인: $ManifestPath"
$summary | ForEach-Object { Write-Host $_ }
Write-Host "잔존 락 키(좌석+유저·경기): ${redisKeyCount}건"
if (-not $Apply) { Write-Host '삭제하지 않았습니다. 측정 결과를 확인한 뒤 -Apply를 붙여 다시 실행해주세요.'; return }

Write-Host '[1/2] 잔존 락 키를 삭제합니다.'
$deletedRedisKeyCount = Invoke-RedisKeyCommand -Command 'DEL' -Keys $redisKeys
Write-Host "삭제한 락 키: ${deletedRedisKeyCount}건"

Write-Host '[2/2] manifest에 기록된 DB 데이터를 삭제합니다.'
Invoke-PerformanceMySql -Sql @"
START TRANSACTION;
DELETE rs FROM reservation_seats rs JOIN reservations r ON r.id = rs.reservation_id WHERE r.game_id = $gameId AND r.user_id IN ($userIdList);
DELETE FROM reservations WHERE game_id = $gameId AND user_id IN ($userIdList);
DELETE FROM admission_tokens WHERE game_id = $gameId AND user_id IN ($userIdList);
DELETE FROM game_seats WHERE game_id = $gameId;
DELETE FROM games WHERE id = $gameId AND title LIKE '[Reservation 성능테스트 $runId]%';
DELETE FROM users WHERE id IN ($cleanupUserIdList) AND email IN ($emailList);
COMMIT;
"@ | Out-Null
$manifest.cleanup.appliedAt = (Get-Date).ToString('o')
[System.IO.File]::WriteAllText($ManifestPath, ($manifest | ConvertTo-Json -Depth 8), [System.Text.UTF8Encoding]::new($false))
Write-Host '정리가 완료됐습니다. manifest는 보존했습니다.'