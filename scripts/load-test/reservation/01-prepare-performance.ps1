# reservation 성능 테스트 전용 경기·좌석·사용자·입장 토큰(admission_tokens)을 준비합니다.
# 대기열 진입 흐름은 이번 측정 범위가 아니므로, admission_tokens는 실제 SSE 흐름 없이 SQL로 직접 시딩합니다.
# Windows: pwsh.exe -NoProfile -File "./scripts/load-test/reservation/01-prepare-performance.ps1"
# macOS/Linux: pwsh -NoProfile -File "./scripts/load-test/reservation/01-prepare-performance.ps1"

param(
    [long]$SourceGameId = 0,

    # 유저 수만큼 서로 다른 VU-좌석 쌍이 필요하므로 좌석 락(userGameLock)이 유저별로 분리되도록, 유저 수는 반드시 VU 수와 같게 지정한다.
    [ValidateRange(1, 2000)]
    [int]$UserCount = 500,

    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl = "http://localhost:8080",

    [ValidateNotNullOrEmpty()]
    [string]$TestPassword = "Test123!",

    [ValidatePattern('^[a-z0-9-]{3,60}$')]
    [string]$RunId = "",

    [ValidateRange(10, 180)]
    [int]$TestDurationMinutes = 60,

    [ValidateRange(30, 300)]
    [int]$GameStartLeadMinutes = 60,

    [ValidateRange(1, 50)]
    [int]$LoginConcurrency = 10,

    [string]$MySqlService = "mysql-db",
    [string]$RedisService = "redis"
)

$ErrorActionPreference = "Stop"
$script:RepoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$script:ComposeFile = Join-Path $script:RepoRoot "docker-compose.yml"

. (Join-Path $PSScriptRoot 'common-performance.ps1')

function Get-KoreaNow {
    try { $timeZone = [TimeZoneInfo]::FindSystemTimeZoneById("Asia/Seoul") }
    catch { $timeZone = [TimeZoneInfo]::FindSystemTimeZoneById("Korea Standard Time") }
    [TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow, $timeZone)
}

function Write-JsonFile {
    param([string]$Path, $Value)
    [System.IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    [System.IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 8), [System.Text.UTF8Encoding]::new($false))
}

if ($PSVersionTable.PSVersion.Major -lt 7) { throw "이 스크립트는 PowerShell 7 이상에서 실행해주세요." }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw "Docker CLI를 찾을 수 없습니다." }
if (-not (Test-Path -LiteralPath $script:ComposeFile)) { throw "docker-compose.yml을 찾을 수 없습니다. re-seat 저장소에서 실행해주세요." }

$uri = $null
if (-not [Uri]::TryCreate($BaseUrl, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -notin @('http', 'https')) {
    throw "BaseUrl은 HTTP 또는 HTTPS 절대 URI여야 합니다. 주소: $BaseUrl"
}
$BaseUrl = $BaseUrl.TrimEnd('/')
$koreaNow = Get-KoreaNow
if ([string]::IsNullOrWhiteSpace($RunId)) { $RunId = "reservation-performance-$($koreaNow.ToString('yyyyMMdd-HHmmss'))" }
$runDirectory = Join-Path $script:RepoRoot "build/k6/runs/$RunId"
$manifestPath = Join-Path $runDirectory "manifest.json"
$usersPath = Join-Path $runDirectory "users.json"
if (Test-Path -LiteralPath $manifestPath) { throw "같은 RunId의 manifest가 이미 있습니다. RunId를 새로 지정해주세요." }

# 1. 테스트 경기 생성 (오늘 경기를 원본으로 복제)
if ($SourceGameId -eq 0) {
    $SourceGameId = [long](Invoke-PerformanceMySql -Scalar -Sql @"
SET time_zone = '+09:00';
SELECT id FROM games
WHERE DATE(game_at) = CURRENT_DATE()
ORDER BY game_at, id LIMIT 1;
"@)
}
if ($SourceGameId -le 0) { throw "KST 오늘의 원본 경기를 찾지 못했습니다. -SourceGameId로 지정해주세요." }

Write-Host "[1/5] 원본 경기 ID ${SourceGameId}에서 reservation 성능 테스트 경기를 복사합니다."
$testGameId = [long](Invoke-PerformanceMySql -Scalar -Sql @"
START TRANSACTION;
SET time_zone = '+09:00';
SET @now_kst = NOW();
SET @booking_close_at = DATE_ADD(@now_kst, INTERVAL $TestDurationMinutes MINUTE);
SET @game_at = DATE_ADD(@booking_close_at, INTERVAL $GameStartLeadMinutes MINUTE);
INSERT INTO games (home_team_id, away_team_id, stadium_id, game_at, booking_open_at, booking_close_at, booking_status, title)
SELECT home_team_id, away_team_id, stadium_id, @game_at, DATE_SUB(@now_kst, INTERVAL 1 MINUTE), @booking_close_at, 'OPEN',
       LEFT(CONCAT('[Reservation 성능테스트 $RunId] ', COALESCE(title, '')), 255)
FROM games WHERE id = $SourceGameId;
SET @test_game_id = LAST_INSERT_ID();
COMMIT;
SELECT @test_game_id;
"@)
if ($testGameId -le 0) { throw "성능 테스트 경기를 만들지 못했습니다. SourceGameId=$SourceGameId" }

$expectedAdminEmail = "reservation-admin-$RunId@reseat.local"
$manifest = [ordered]@{
    testType = 'reservation-performance'; runId = $RunId; preparedAt = (Get-KoreaNow).ToString('o'); baseUrl = $BaseUrl
    sourceGameId = $SourceGameId; testGameId = $testGameId; gameSeatIds = @()
    adminUser = $null; expectedAdminEmail = $expectedAdminEmail
    expectedUserCount = $UserCount; users = @(); expectedUserEmails = @()
    cleanup = [ordered]@{ appliedAt = $null }
}
Write-JsonFile -Path $manifestPath -Value $manifest

# 2. 임시 관리자로 좌석 재고 오픈
$temporaryAdmin = [PSCustomObject]@{
    email = $expectedAdminEmail; name = "Reservation 성능 테스트 관리자"; nickname = "resv-admin"
    phone = "010-$($koreaNow.ToString('HHmm'))-$([Random]::new().Next(0, 10000).ToString('0000'))"
}
$adminSignupBody = @{ email = $temporaryAdmin.email; password = $TestPassword; name = $temporaryAdmin.name; nickname = $temporaryAdmin.nickname; phone = $temporaryAdmin.phone } | ConvertTo-Json -Compress
Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/signup" -Method Post -ContentType "application/json" -Body $adminSignupBody | Out-Null
$adminRows = @(Invoke-PerformanceMySql -NoHeaders -Sql "SELECT id, email FROM users WHERE email = '$($temporaryAdmin.email)' LIMIT 1;" | ForEach-Object {
    $columns = $_ -split "`t", 2
    [PSCustomObject]@{ userId = [long]$columns[0]; email = $columns[1] }
})
if ($adminRows.Count -ne 1) { throw "성능 테스트 관리자 계정을 준비하지 못했습니다." }
$manifest.adminUser = $adminRows[0]
Write-JsonFile -Path $manifestPath -Value $manifest
$adminUserId = [long]$adminRows[0].userId

Invoke-PerformanceMySql -Sql "UPDATE users SET role = 'ADMIN', status = 'ACTIVE', is_verified = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = $adminUserId;" | Out-Null
$encodedTestPassword = [string](Invoke-PerformanceMySql -Scalar -Sql "SELECT password FROM users WHERE id = $adminUserId;")
if ([string]::IsNullOrWhiteSpace($encodedTestPassword)) { throw "테스트 사용자에게 적용할 암호화 비밀번호를 찾지 못했습니다." }
$adminLoginBody = @{ email = $temporaryAdmin.email; password = $TestPassword } | ConvertTo-Json -Compress
$adminLoginResponse = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $adminLoginBody
if ([string]::IsNullOrWhiteSpace([string]$adminLoginResponse.data.accessToken)) { throw "임시 관리자 Access Token을 발급하지 못했습니다." }

Write-Host "[2/5] 좌석 재고를 오픈합니다."
$adminHeaders = @{ Authorization = "Bearer $($adminLoginResponse.data.accessToken)" }
try {
    $seatOpenResponse = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/games/$testGameId/seats" -Method Post -Headers $adminHeaders
} catch {
    throw "테스트 경기 좌석 재고 오픈 API 호출에 실패했습니다. 테스트 경기 ID: $testGameId"
}
if (-not $seatOpenResponse.success -or [int]$seatOpenResponse.data.gameId -ne $testGameId -or [int]$seatOpenResponse.data.createdCount -le 0) {
    throw "좌석 재고 오픈 결과가 올바르지 않습니다. 테스트 경기 ID: $testGameId"
}
$gameSeatIds = @(Invoke-PerformanceMySql -NoHeaders -Sql "SELECT id FROM game_seats WHERE game_id = $testGameId ORDER BY id;" | ForEach-Object { [long]$_.ToString().Trim() })
if ($gameSeatIds.Count -ne [int]$seatOpenResponse.data.createdCount) { throw "좌석 생성 수가 API 응답과 다릅니다." }
if ($gameSeatIds.Count -lt $UserCount) { throw "좌석 수($($gameSeatIds.Count))가 유저 수($UserCount)보다 적습니다. 시나리오 B/C를 위해 좌석 수 >= 유저 수여야 합니다." }
$manifest.gameSeatIds = $gameSeatIds
Write-JsonFile -Path $manifestPath -Value $manifest

# 3. 테스트 사용자 생성
# VU 1개 = 유저 1명
# UserGameLockStrategy가 userId+gameId 단위이므로 같은 유저를 여러 VU가 공유하면
# 바깥쪽 락에서 의도치 않게 직렬화되어 시나리오 B/C의 "전역 락 아님" 측정이 오염된다.
$phonePrefix = $koreaNow.ToString('mmss')
$usersToCreate = @(1..$UserCount | ForEach-Object {
    $sequence = $_.ToString('0000')
    [PSCustomObject]@{
        email = "reservation-user-$RunId-$sequence@reseat.local"
        name = "Reservation 성능 테스트 $sequence"
        nickname = "resv-$sequence"
        phone = "010-$phonePrefix-$sequence"
    }
})
$manifest.expectedUserEmails = @($usersToCreate | ForEach-Object { $_.email })
Write-JsonFile -Path $manifestPath -Value $manifest

Write-Host "[3/5] 성능 테스트 사용자 ${UserCount}명을 준비합니다."
$emailList = (($usersToCreate | ForEach-Object { "'$($_.email)'" }) -join ',')
for ($offset = 0; $offset -lt $usersToCreate.Count; $offset += 100) {
    $lastIndex = [Math]::Min($offset + 99, $usersToCreate.Count - 1)
    $batch = @($usersToCreate[$offset..$lastIndex])
    $values = @($batch | ForEach-Object {
        $email = $_.email.Replace("'", "''"); $name = $_.name.Replace("'", "''")
        $nickname = $_.nickname.Replace("'", "''"); $phone = $_.phone.Replace("'", "''")
        "('$email', '$encodedTestPassword', '$name', '$nickname', '$phone', 'USER', 'ACTIVE', TRUE)"
    }) -join ",`n"
    Invoke-PerformanceMySql -Sql @"
INSERT INTO users (email, password, name, nickname, phone, role, status, is_verified)
VALUES $values;
"@ | Out-Null
    Write-Host "사용자 생성 진행: $($lastIndex + 1)/${UserCount}"
}
$createdUsers = @(Invoke-PerformanceMySql -NoHeaders -Sql "SELECT id, email FROM users WHERE email IN ($emailList) ORDER BY id;" | ForEach-Object {
    $columns = $_ -split "`t", 2
    [PSCustomObject]@{ userId = [long]$columns[0]; email = $columns[1] }
})
if ($createdUsers.Count -ne $UserCount) { throw "사용자 생성 완료 수가 요청 수와 다릅니다." }
$manifest.users = $createdUsers
Write-JsonFile -Path $manifestPath -Value $manifest
$userIdList = ($createdUsers | ForEach-Object { $_.userId }) -join ','
Invoke-PerformanceMySql -Sql "UPDATE users SET status = 'ACTIVE', is_verified = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id IN ($userIdList);" | Out-Null

# 4. 입장 토큰(admission_tokens)을 SQL로 직접 시딩
# 대기열 SSE 흐름은 측정 범위 밖이다.
# 좌석 선점은 검증만 하고 소비하지 않으므로 만료 시각을 측정 시간보다 충분히 길게 잡아 회차 반복에도 재사용 가능하게 한다.
Write-Host "[4/5] 입장 토큰(admission_tokens)을 유저별로 발급합니다."
Invoke-PerformanceMySql -Sql @"
INSERT INTO admission_tokens (game_id, user_id, token, status, issued_at, expires_at, seat_browsing_expires_at)
SELECT $testGameId, id, UUID(), 'ACTIVE', NOW(), DATE_ADD(NOW(), INTERVAL $TestDurationMinutes MINUTE), DATE_ADD(NOW(), INTERVAL $TestDurationMinutes MINUTE)
FROM users WHERE id IN ($userIdList);
"@ | Out-Null

# 5. 사용자별 Access Token + 자신의 입장 토큰을 묶어 users.json으로 저장
Write-Host "[5/5] 사용자별 Access Token을 발급합니다."
$loginBatchSize = [Math]::Max($LoginConcurrency, 25)
$authenticatedUsers = @()
for ($offset = 0; $offset -lt $createdUsers.Count; $offset += $loginBatchSize) {
    $lastIndex = [Math]::Min($offset + $loginBatchSize - 1, $createdUsers.Count - 1)
    $batch = @($createdUsers[$offset..$lastIndex])
    $batchResults = @($batch | ForEach-Object -Parallel {
        $user = $_
        try {
            $body = @{ email = $user.email; password = $using:TestPassword } | ConvertTo-Json -Compress
            $response = Invoke-RestMethod -Uri "$using:BaseUrl/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $body -ErrorAction Stop
            if ([string]::IsNullOrWhiteSpace([string]$response.data.accessToken)) { throw "로그인 응답에 Access Token이 없습니다." }
            [PSCustomObject]@{ success = $true; userId = $user.userId; email = $user.email; accessToken = $response.data.accessToken; error = $null }
        } catch {
            [PSCustomObject]@{ success = $false; userId = $user.userId; email = $user.email; accessToken = $null; error = $_.Exception.Message }
        }
    } -ThrottleLimit $LoginConcurrency)
    $failedLogins = @($batchResults | Where-Object { -not $_.success })
    if ($failedLogins.Count -gt 0) { throw "Access Token 발급 실패: $($failedLogins[0].email) - $($failedLogins[0].error)" }
    $authenticatedUsers += @($batchResults | Select-Object userId, email, accessToken)
    Write-Host "로그인 진행: $($lastIndex + 1)/${UserCount}"
}
$admissionTokenRows = @(Invoke-PerformanceMySql -NoHeaders -Sql "SELECT user_id, token FROM admission_tokens WHERE game_id = $testGameId AND user_id IN ($userIdList);" | ForEach-Object {
    $columns = $_ -split "`t", 2
    [PSCustomObject]@{ userId = [long]$columns[0]; admissionToken = $columns[1] }
})
$admissionTokenMap = @{}
$admissionTokenRows | ForEach-Object { $admissionTokenMap[$_.userId] = $_.admissionToken }

# VU마다 서로 다른 좌석을 고정 배정할 수 있도록 좌석 ID도 함께 내려준다(1:1 매핑).
$authenticatedUsers = @($authenticatedUsers | Sort-Object userId | ForEach-Object {
    $index = [array]::IndexOf(@($authenticatedUsers.userId | Sort-Object), $_.userId)
    [PSCustomObject]@{
        userId = $_.userId; email = $_.email; accessToken = $_.accessToken
        admissionToken = $admissionTokenMap[$_.userId]
        assignedSeatId = $gameSeatIds[$index]
    }
})
if ($authenticatedUsers.Count -ne $UserCount) { throw "Access Token 발급 완료 수가 요청 수와 다릅니다." }
Write-JsonFile -Path $usersPath -Value $authenticatedUsers

Write-Host "reservation 성능 테스트 데이터 준비가 완료됐습니다."
Write-Host "테스트 경기 ID: $testGameId / 좌석 수: $($gameSeatIds.Count) / 사용자 수: $($authenticatedUsers.Count)"
Write-Host "manifest: $manifestPath"
Write-Host "k6 사용자 데이터: $usersPath"
Write-Host "Access Token 유효시간은 1시간, 입장 토큰 유효시간은 ${TestDurationMinutes}분입니다."