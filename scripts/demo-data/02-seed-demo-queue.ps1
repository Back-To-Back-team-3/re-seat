# 2번 선택 실행: 1번 준비 후 대기열 진행을 확인할 가상 사용자와 WAITING 데이터를 만듭니다.
# Windows: powershell.exe -ExecutionPolicy Bypass -File "./scripts/demo-data/02-seed-demo-queue.ps1" -GameId 117 -UserCount 100
# macOS: pwsh -NoProfile -File "./scripts/demo-data/02-seed-demo-queue.ps1" -GameId 117 -UserCount 100
# 다음 순서: 확인이 끝나면 03-reset-demo-queue.ps1로 대기열만 비웁니다.

param(
    [long]$GameId = 117,
    [ValidateRange(1, 500)]
    [int]$UserCount = 100
)

. "$PSScriptRoot/common.ps1"

Assert-DemoServices

$preparedGame = [int](Invoke-DemoMySql -Scalar -Sql @"
SELECT COUNT(*)
FROM games
WHERE id = $GameId
  AND booking_status = 'OPEN';
"@)

if ($preparedGame -ne 1) {
    throw "경기 ID ${GameId}가 OPEN 상태가 아닙니다. 01-setup-demo-data.ps1을 먼저 실행해주세요."
}

Write-Host "[1/3] 기존 경기 대기열을 초기화합니다."
Clear-DemoRedisQueues -GameIds @($GameId)
Invoke-DemoMySql -Sql @"
DELETE FROM admission_tokens WHERE game_id = $GameId;
DELETE FROM queue_entry_histories WHERE game_id = $GameId;
"@ | Out-Null

Write-Host "[2/3] 대기열 데모 사용자 ${UserCount}명을 준비합니다."
$userValues = 1..$UserCount | ForEach-Object {
    $sequence = $_.ToString("000")
    "('queue-demo-$sequence@reseat.local', NULL, '대기 사용자 $sequence', '대기$sequence', NULL, 'USER', 'ACTIVE', NULL, TRUE, '$script:DemoProvider', 'queue-demo-$sequence')"
}

Invoke-DemoMySql -Sql @"
INSERT INTO users (
    email, password, name, nickname, phone, role, status,
    ci, is_verified, provider, provider_id
)
VALUES
$($userValues -join ",`n")
ON DUPLICATE KEY UPDATE
    status = 'ACTIVE',
    is_verified = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO queue_entry_histories (
    game_id, user_id, queue_key, status, entered_at
)
SELECT
    $GameId,
    id,
    CONCAT('queue:game:${GameId}:user:', id),
    'WAITING',
    NOW()
FROM users
WHERE provider = '$script:DemoProvider'
  AND provider_id LIKE 'queue-demo-%'
ORDER BY provider_id
LIMIT $UserCount;
"@ | Out-Null

$userIds = Invoke-DemoMySql -NoHeaders -Sql @"
SELECT id
FROM users
WHERE provider = '$script:DemoProvider'
  AND provider_id LIKE 'queue-demo-%'
ORDER BY provider_id
LIMIT $UserCount;
"@

if (@($userIds).Count -ne $UserCount) {
    throw "데모 사용자 ID 조회 수가 요청 수와 다릅니다."
}

Write-Host "[3/3] Redis ZSet에 대기 순서를 등록합니다."
$redisArguments = [System.Collections.Generic.List[string]]::new()
$redisArguments.Add("ZADD")
$redisArguments.Add("queue:game:$GameId")
$baseScore = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - ($UserCount + 10)

for ($index = 0; $index -lt $userIds.Count; $index++) {
    $redisArguments.Add(($baseScore + $index).ToString())
    $redisArguments.Add("user:$($userIds[$index])")
}

Invoke-DemoRedis -Arguments $redisArguments.ToArray() | Out-Null

$remaining = Invoke-DemoRedis -Arguments @("ZCARD", "queue:game:$GameId")
Write-Host "경기 ID ${GameId}에 대기 사용자 ${remaining}명이 등록됐습니다."
Write-Host "스케줄러가 3초마다 최대 20명씩 입장시키므로 지금 브라우저에서 [대기열 체험] 경기의 예매 시작을 눌러주세요."
