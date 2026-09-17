# reservation 성능 테스트 회차 사이에 좌석·예약 상태만 초기화힌다.
# 사용자·경기·입장 토큰(admission_tokens)은 재사용을 위해 삭제하지 않는다 (Queue-Token은 검증만 하고 소비하지 않으므로 회차 반복에 그대로 재사용 가능).
#
# 주의(장시간 세션):
# Access Token 유효기간은 60분 고정(JwtTokenProvider)이며 TestDurationMinutes와 무관하다. # 준비(01-prepare) 후 60분을 넘겨 회차를 실행하면 admission_tokens가 유효해도 Access Token 만료로 401이 발생한다.
# -RefreshAccessTokens를 지정하면 users.json의 각 유저를 재로그인해 accessToken만 갱신한다(admissionToken·assignedSeatId는 그대로 유지).
# Windows: pwsh.exe -NoProfile -File "./scripts/load-test/reservation/02-reset-performance.ps1" -ManifestPath "<manifest 경로>" -Apply
# macOS/Linux: pwsh -NoProfile -File "./scripts/load-test/reservation/02-reset-performance.ps1" -ManifestPath "<manifest 경로>" -Apply

param(
    [string]$ManifestPath = "",
    [switch]$Apply,
    # 준비 후 60분(Access Token 유효기간)을 넘겨 다음 회차를 실행할 때 지정한다.
    [switch]$RefreshAccessTokens,
    [string]$TestPassword = "Test123!",
    [string]$BaseUrl = "http://localhost:8080",
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

# manifest.gameSeatIds가 실제로 이 경기(gameId) 소속인지 재확인한다.
# manifest가 오래됐거나 잘못 지정된 경우, 검증 없이 그대로 쓰면 다른 경기의 활성 Redis 락 키를 잘못 삭제할 위험이 있다.
# game_seats.id는 IDENTITY라 정상 흐름에서는 안전하지만, 사람이 manifest를 잘못 지정하는 경우까지 방어한다.
if ($gameSeatIds.Count -gt 0) {
    $gameSeatIdList = $gameSeatIds -join ','
    $actualSeatCount = [int](Invoke-PerformanceMySql -Scalar -Sql "SELECT COUNT(*) FROM game_seats WHERE game_id = $gameId AND id IN ($gameSeatIdList);")
    if ($actualSeatCount -ne $gameSeatIds.Count) {
        throw "manifest의 gameSeatIds 중 일부가 이 경기($gameId) 소속이 아닙니다. manifest를 다시 확인해주세요."
    }
}

# 이 경기에 manifest 유저 외 다른 사용자의 예약이 있는지 확인한다.
$unexpectedUsers = [int](Invoke-PerformanceMySql -Scalar -Sql @"
SELECT COUNT(*) FROM reservations WHERE game_id = $gameId AND user_id NOT IN ($userIdList);
"@)
if ($unexpectedUsers -ne 0) {
    throw "다른 사용자와 연결된 예약이 ${unexpectedUsers}건 있습니다. 좌석 상태를 초기화하지 않습니다."
}

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

if ($RefreshAccessTokens) {
    Write-Host '[추가] Access Token 만료 가능성에 대비해 재로그인합니다.'
    $usersPath = Join-Path (Split-Path -Parent $ManifestPath) 'users.json'
    $existingUsers = Get-Content -LiteralPath $usersPath -Raw | ConvertFrom-Json
    $refreshedUsers = @($existingUsers | ForEach-Object {
        $loginBody = @{ email = $_.email; password = $TestPassword } | ConvertTo-Json -Compress
        $loginResponse = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
        if ([string]::IsNullOrWhiteSpace([string]$loginResponse.data.accessToken)) {
            throw "Access Token 재발급 실패: $($_.email)"
        }
        # admissionToken·assignedSeatId는 그대로 유지하고 accessToken만 교체한다.
        [PSCustomObject]@{
            userId = $_.userId; email = $_.email
            accessToken = $loginResponse.data.accessToken
            admissionToken = $_.admissionToken
            assignedSeatId = $_.assignedSeatId
        }
    })
    [System.IO.File]::WriteAllText($usersPath, ($refreshedUsers | ConvertTo-Json -Depth 3), [System.Text.UTF8Encoding]::new($false))
    Write-Host "Access Token을 재발급했습니다: $usersPath"
}

Write-Host '초기화가 완료됐습니다. 다음 회차를 실행할 수 있습니다.'