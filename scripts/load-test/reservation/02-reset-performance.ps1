# reservation 성능 테스트 회차 사이에 좌석·예약 상태만 초기화합니다.
# 사용자·경기·입장 토큰(admission_tokens)은 재사용을 위해 삭제하지 않습니다.
# (Queue-Token은 검증만 하고 소비하지 않으므로 회차 반복에 그대로 재사용 가능)
# Windows: pwsh.exe -NoProfile -File "./scripts/load-test/reservation/02-reset-performance.ps1" -ManifestPath "<manifest 경로>" -Apply
# macOS/Linux: pwsh -NoProfile -File "./scripts/load-test/reservation/02-reset-performance.ps1" -ManifestPath "<manifest 경로>" -Apply

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
if ($runId -notmatch '^[a-z0-9-]{3,60}$') { throw 'manifest의 RunId 형식이 올바르지 않습니다.' }
$userIds = @($manifest.users | ForEach-Object { [long]$_.userId })
$gameSeatIds = @($manifest.gameSeatIds | ForEach-Object { [long]$_ })
if ($gameId -le 0 -or $userIds.Count -eq 0) { throw '초기화할 테스트 경기 또는 사용자 ID가 manifest에 없습니다.' }
$userIdList = $userIds -join ','

$testGameCount = [int](Invoke-PerformanceMySql -Scalar -Sql "SELECT COUNT(*) FROM games WHERE id = $gameId AND title LIKE '[Reservation 성능테스트 $runId]%';")
if ($testGameCount -ne 1) { throw 'manifest의 경기 ID가 이 실행의 성능 테스트 경기인지 확인하지 못했습니다.' }

$summary = Invoke-PerformanceMySql -Sql @"
SELECT 'reservation_seats' AS target, COUNT(*) AS count FROM reservation_seats rs JOIN reservations r ON r.id = rs.reservation_id WHERE r.game_id = $gameId AND r.user_id IN ($userIdList)
UNION ALL SELECT 'reservations', COUNT(*) FROM reservations WHERE game_id = $gameId AND user_id IN ($userIdList)
UNION ALL SELECT 'game_seats(HELD)', COUNT(*) FROM game_seats WHERE game_id = $gameId AND status = 'HELD';
"@

# 락 키를 SCAN이 아니라 manifest의 실제 ID로 명시적으로 구성한다(전체 Redis 키스페이스를 훑지 않기 위함).
$redisPassword = Get-ComposeSecret -Name 'REDIS_PASSWORD'
$seatLockKeys = @($gameSeatIds | ForEach-Object { "lock:game-seat:$_" })
$userGameLockKeys = @($userIds | ForEach-Object { "lock:seat-hold:user:${_}:game:$gameId" })
$redisKeys = $seatLockKeys + $userGameLockKeys
$staleLockKeyCount = Invoke-RedisKeyCommand -Command 'EXISTS' -Keys $redisKeys

Write-Host "초기화 대상 확인: $ManifestPath"
$summary | ForEach-Object { Write-Host $_ }
Write-Host "잔존 락 키(좌석+유저·경기): ${staleLockKeyCount}건"
if ($staleLockKeyCount -gt 0) {
    Write-Warning "잔존 락 키 발견 — watchdog 갱신이 끊긴 크래시 스레드가 있었을 수 있습니다. 다음 회차 전 원인을 확인하세요."
}
if (-not $Apply) { Write-Host '초기화하지 않았습니다. -Apply를 붙여 다시 실행해주세요.'; return }

Invoke-PerformanceMySql -Sql @"
START TRANSACTION;
DELETE rs FROM reservation_seats rs JOIN reservations r ON r.id = rs.reservation_id WHERE r.game_id = $gameId AND r.user_id IN ($userIdList);
DELETE FROM reservations WHERE game_id = $gameId AND user_id IN ($userIdList);
UPDATE game_seats SET status = 'AVAILABLE', hold_expires_at = NULL WHERE game_id = $gameId AND status = 'HELD';
COMMIT;
"@ | Out-Null

$deletedLockKeyCount = Invoke-RedisKeyCommand -Command 'DEL' -Keys $redisKeys
Write-Host "정리한 잔존 락 키: ${deletedLockKeyCount}건"
Write-Host '초기화가 완료됐습니다. 다음 회차를 실행할 수 있습니다.'