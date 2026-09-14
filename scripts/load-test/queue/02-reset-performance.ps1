# Queue 성능 테스트의 다음 k6 단계 전 Queue 상태만 초기화합니다.
# 기본 실행은 삭제하지 않습니다. Kafka 처리가 끝난 뒤 -Apply를 붙여 실행합니다.
# Windows: powershell.exe -ExecutionPolicy Bypass -File "./scripts/load-test/queue/02-reset-performance.ps1" -ManifestPath "<manifest 경로>" -Apply
# macOS/Linux: pwsh -NoProfile -File "./scripts/load-test/queue/02-reset-performance.ps1" -ManifestPath "<manifest 경로>" -Apply

param(
    # dry-run에서는 생략할 수 있지만 -Apply를 사용할 때는 확인한 manifest 경로를 반드시 지정한다.
    [string]$ManifestPath = "",
    [switch]$Apply,
    [string]$MySqlService = "mysql-db",
    [string]$RedisService = "redis"
)

$ErrorActionPreference = "Stop"
$script:RepoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$script:ComposeFile = Join-Path $script:RepoRoot "docker-compose.yml"

. (Join-Path $PSScriptRoot 'common-performance.ps1')

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker CLI를 찾을 수 없습니다.' }
if (-not (Test-Path -LiteralPath $script:ComposeFile)) { throw 'docker-compose.yml을 찾을 수 없습니다. re-seat 저장소에서 실행해주세요.' }
$ManifestPath = Resolve-ManifestPath
if (-not (Test-Path -LiteralPath $ManifestPath)) { throw "manifest 파일을 찾을 수 없습니다. 경로: $ManifestPath" }
$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
if ($manifest.testType -ne 'queue-performance' -or [string]::IsNullOrWhiteSpace([string]$manifest.runId)) { throw 'Queue 성능 테스트에서 만든 manifest가 아닙니다.' }

$gameId = [long]$manifest.testGameId
$runId = [string]$manifest.runId
if ($runId -notmatch '^[a-z0-9-]{3,60}$') { throw 'manifest의 RunId 형식이 올바르지 않습니다.' }
$userIds = @($manifest.users | ForEach-Object { [long]$_.userId })
if ($gameId -le 0 -or $userIds.Count -eq 0) { throw '초기화할 테스트 경기 또는 사용자 ID가 manifest에 없습니다.' }
$userIdList = $userIds -join ','

# 테스트 경기와 이번 RunId의 사용자 관계만 초기화 대상으로 확인한다.
$testGameCount = [int](Invoke-PerformanceMySql -Scalar -Sql "SELECT COUNT(*) FROM games WHERE id = $gameId AND title LIKE '[Queue 성능테스트 $runId]%';")
if ($testGameCount -ne 1) { throw 'manifest의 경기 ID가 이 실행의 성능 테스트 경기인지 확인하지 못했습니다.' }

$summary = Invoke-PerformanceMySql -Sql @"
SELECT 'queue_entry_histories' AS target, COUNT(*) AS count FROM queue_entry_histories WHERE game_id = $gameId AND user_id IN ($userIdList)
UNION ALL SELECT 'admission_tokens', COUNT(*) FROM admission_tokens WHERE game_id = $gameId AND user_id IN ($userIdList);
"@
$redisPassword = Get-ComposeSecret -Name 'REDIS_PASSWORD'
$redisKeys = @("queue:waiting:game:$gameId", "lock:queue:admit:$gameId")
foreach ($userId in $userIds) {
    $redisKeys += "queue:entry:game:$gameId:user:$userId"
    $redisKeys += "queue:entry:rejection:game:$gameId:user:$userId"
    $redisKeys += "queue:entry:request:latest:game:$gameId:user:$userId"
}
$redisKeyCount = Invoke-RedisKeyCommand -Command 'EXISTS' -Keys $redisKeys
Write-Host "Queue 초기화 대상 확인: $ManifestPath"
$summary | ForEach-Object { Write-Host $_ }
Write-Host "Queue Redis Key: ${redisKeyCount}건"
if (-not $Apply) { Write-Host '초기화하지 않았습니다. Kafka 처리 완료를 확인한 뒤 -Apply를 붙여 다시 실행해주세요.'; return }

# Queue DB와 Redis 상태만 비우며 경기·좌석·사용자·주문 데이터는 유지한다.
Invoke-PerformanceMySql -Sql @"
START TRANSACTION;
DELETE FROM admission_tokens WHERE game_id = $gameId AND user_id IN ($userIdList);
DELETE FROM queue_entry_histories WHERE game_id = $gameId AND user_id IN ($userIdList);
COMMIT;
"@ | Out-Null

$deletedRedisKeyCount = Invoke-RedisKeyCommand -Command 'DEL' -Keys $redisKeys
Write-Host "삭제한 Queue Redis Key: ${deletedRedisKeyCount}건"
Write-Host 'Queue 초기화가 완료됐습니다. 다음 k6 단계를 실행할 수 있습니다.'
