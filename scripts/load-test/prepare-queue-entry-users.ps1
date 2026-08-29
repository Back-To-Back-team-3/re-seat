# 로컬 Docker 환경에서 대기열 진입 부하 테스트용 사용자와 Access Token 데이터를 준비합니다.
# 대상 경기의 기존 대기열과 queue-load-* 테스트 사용자를 초기화합니다.
# 02-seed-demo-queue.ps1를 수정해서 작성했으므로 01-setup-demo-data.ps1를 미리 실행해야 합니다.
# Windows: powershell.exe -ExecutionPolicy Bypass -File "./scripts/load-test/prepare-queue-entry-users.ps1" -GameId 117 -UserCount 500
# macOS: pwsh -NoProfile -File "./scripts/load-test/prepare-queue-entry-users.ps1" -GameId 117 -UserCount 500

param(
    [long]$GameId = 117,

    [ValidateRange(1, 2000)]
    [int]$UserCount = 500,

    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl = "http://localhost:8080",

    [ValidateNotNullOrEmpty()]
    [string]$TestPassword = "Test123!"
)

. "$PSScriptRoot/../demo-data/common.ps1"

# Docker Compose 서비스 실행 상태 및 백엔드 서버 상태를 확인합니다.
Assert-DemoServices

try {
    $healthResponse = Invoke-RestMethod `
        -Uri "$BaseUrl/actuator/health" `
        -Method Get
} catch {
    throw "백엔드 서버에 연결할 수 없습니다. 주소: $BaseUrl"
}

if ($healthResponse.status -ne "UP") {
    throw "백엔드 서버가 정상 상태가 아닙니다."
}

# 대상 경기가 OPEN 상태인지 확인합니다.
$preparedGame = [int](Invoke-DemoMySql -Scalar -Sql @"
SELECT COUNT(*)
FROM games
WHERE id = $GameId
  AND booking_status = 'OPEN';
"@)

if ($preparedGame -ne 1) {
    throw "경기 ID ${GameId}가 OPEN 상태가 아닙니다."
}

# 기존 Redis 대기열·입장 토큰·Queue 이력 초기화합니다.
Write-Host "[1/3] 기존 경기 대기열을 초기화합니다."

Clear-DemoRedisQueues -GameIds @($GameId)

Invoke-DemoMySql -Sql @"
DELETE FROM admission_tokens WHERE game_id = $GameId;
DELETE FROM queue_entry_histories WHERE game_id = $GameId;
"@ | Out-Null

# 이전 실행의 부하 테스트 사용자와 연결된 Queue 데이터를 제거합니다.
Invoke-DemoMySql -Sql @"
DELETE at
FROM admission_tokens at
JOIN users u ON u.id = at.user_id
WHERE u.email LIKE 'queue-load-%@reseat.local';

DELETE qeh
FROM queue_entry_histories qeh
JOIN users u ON u.id = qeh.user_id
WHERE u.email LIKE 'queue-load-%@reseat.local';

DELETE FROM users
WHERE email LIKE 'queue-load-%@reseat.local';
"@ | Out-Null

# 이전 사용자를 삭제한 뒤 부하 테스트 사용자를 새로 생성합니다.
Write-Host "[2/3] 대기열 부하 테스트 사용자 ${UserCount}명을 준비합니다."

$usersToCreate = 1..$UserCount | ForEach-Object {
    $sequence = $_.ToString("000")

    [PSCustomObject]@{
        email    = "queue-load-$sequence@reseat.local"
        name     = "부하 사용자 $sequence"
        nickname = "부하$sequence"
        phone    = "010-1234-$($_.ToString("0000"))"
    }
}

$progressInterval = [Math]::Max(1, [int][Math]::Ceiling($UserCount / 10.0))
$signupCount = 0
foreach ($user in $usersToCreate) {
    $signupBody = @{
        email    = $user.email
        password = $TestPassword
        name     = $user.name
        nickname = $user.nickname
        phone    = $user.phone
    } | ConvertTo-Json

    try {
        Invoke-RestMethod `
            -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/auth/signup" `
            -Method Post `
            -ContentType "application/json" `
            -Body $signupBody | Out-Null
    } catch {
        throw "부하 테스트 사용자 회원가입에 실패했습니다. email=$($user.email)"
    }

    $signupCount++

    if ($signupCount % $progressInterval -eq 0 -or $signupCount -eq $UserCount) {
        $signupPercent = [Math]::Min(100, [int][Math]::Floor(($signupCount / $UserCount) * 100))
        Write-Host "회원가입 진행: ${signupCount}/${UserCount} (${signupPercent}%)"
    }
}

# 실제 본인인증 API 호출은 부하 테스트 범위가 아니므로 테스트 사용자 상태만 완료로 맞춥니다.
Invoke-DemoMySql -Sql @"
UPDATE users
SET status = 'ACTIVE',
    is_verified = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE email LIKE 'queue-load-%@reseat.local';
"@ | Out-Null

# 사용자 ID와 이메일 조회
$userRows = @(Invoke-DemoMySql -NoHeaders -Sql @"
SELECT id, email
FROM users
WHERE email LIKE 'queue-load-%@reseat.local'
ORDER BY email
LIMIT $UserCount;
"@)

if (@($userRows).Count -ne $UserCount) {
    throw "부하 테스트 사용자 조회 수가 요청 수와 다릅니다."
}

# MySQL의 탭 구분 조회 결과를 JWT 생성에 사용할 사용자 데이터로 변환합니다.
$users = $userRows | ForEach-Object {
    $columns = $_ -split "`t"

    [PSCustomObject]@{
        userId = [long]$columns[0]
        email = $columns[1]
    }
}

# 각 VU가 독립된 사용자로 요청할 수 있도록 사용자별 Access Token을 발급받습니다.
$progressInterval = [Math]::Max(1, [int][Math]::Ceiling($UserCount / 10.0))
$loginCount = 0
$authenticatedUsers = @($users | ForEach-Object {
    $loginBody = @{
        email    = $_.email
        password = $TestPassword
    } | ConvertTo-Json -Compress

    try {
        $loginResponse = Invoke-RestMethod `
            -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/auth/login" `
            -Method Post `
            -ContentType "application/json" `
            -Body $loginBody
    } catch {
        throw "부하 테스트 사용자 로그인에 실패했습니다. email=$($_.email)"
    }

    if ([string]::IsNullOrWhiteSpace([String]$loginResponse.data.accessToken)) {
        throw "로그인 응답에 Access Token이 없습니다. email=$($_.email)"
    }

    $loginCount++

    if ($loginCount % $progressInterval -eq 0 -or $loginCount -eq $UserCount) {
        $loginPercent = [Math]::Min(100, [int][Math]::Floor(($loginCount / $UserCount) * 100))
        Write-Host "로그인 진행: ${loginCount}/${UserCount} (${loginPercent}%)"
    }

    [PSCustomObject]@{
        userId      = $_.userId
        email       = $_.email
        accessToken = $loginResponse.data.accessToken
    }
})

# Access Token이 포함된 생성 파일은 build/k6 폴더에 저장합니다.
$outputDirectory = Join-Path $script:RepoRoot "build/k6"
$outputFile = Join-Path $outputDirectory "queue-entry-users.json"

[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null

$json = ConvertTo-Json -InputObject @($authenticatedUsers) -Depth 3

[System.IO.File]::WriteAllText(
    $outputFile,
    $json,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Host "[3/3] 사용자별 Access Token 준비가 완료됐습니다."
Write-Host "대상 경기 ID: $GameId"
Write-Host "준비된 사용자 수: $(@($authenticatedUsers).Count)"
Write-Host "k6 사용자 데이터: $outputFile"
Write-Host "Access Token의 유효시간은 1시간이므로 만료되면 이 스크립트를 다시 실행해주세요."
