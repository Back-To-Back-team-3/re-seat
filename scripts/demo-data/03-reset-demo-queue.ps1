# 3번 선택 정리: 2번에서 만든 지정 경기의 대기열·입장 토큰만 제거하고 6경기 데모 데이터는 유지합니다.
# Windows: powershell.exe -ExecutionPolicy Bypass -File "./scripts/demo-data/03-reset-demo-queue.ps1" -GameId 117
# macOS: pwsh -NoProfile -File "./scripts/demo-data/03-reset-demo-queue.ps1" -GameId 117
# 전체 작업을 마칠 때는 04-restore-demo-data.ps1을 실행합니다.

param(
    [long]$GameId = 117
)

. "$PSScriptRoot/common.ps1"

Assert-DemoServices

if ($GameId -notin $script:DemoGameIds) {
    throw "대기열 데모는 준비된 경기 ID 중 하나만 사용할 수 있습니다: $script:DemoGameIdList"
}

Clear-DemoRedisQueues -GameIds @($GameId)

Invoke-DemoMySql -Sql @"
DELETE FROM admission_tokens WHERE game_id = $GameId;
DELETE FROM queue_entry_histories WHERE game_id = $GameId;
"@ | Out-Null

Write-Host "경기 ID ${GameId}의 Redis 대기열, DB 대기 이력과 입장 토큰을 초기화했습니다."
