# Queue 성능 테스트 전용 경기와 사용자를 준비합니다.
# Windows: pwsh.exe -NoProfile -File "./scripts/load-test/queue/01-prepare-performance.ps1"
# macOS/Linux: pwsh -NoProfile -File "./scripts/load-test/queue/01-prepare-performance.ps1"

param(
    # 0이면 KST 오늘의 경기 중 가장 이른 경기를 원본으로 선택한다.
    [long]$SourceGameId = 0,

    # 실행할 VU 수에 맞춰 지정한다.
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

    # 사용자별 Access Token 발급 요청의 최대 동시 실행 수다.
    [ValidateRange(1, 50)]
    [int]$LoginConcurrency = 10,

    [string]$MySqlService = "mysql-db"
)

$ErrorActionPreference = "Stop"
$script:RepoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$script:ComposeFile = Join-Path $script:RepoRoot "docker-compose.yml"

. (Join-Path $PSScriptRoot 'common-performance.ps1')

# 현재 UTC 시각을 한국 표준시로 변환합니다.
function Get-KoreaNow {
    try { $timeZone = [TimeZoneInfo]::FindSystemTimeZoneById("Asia/Seoul") }
    catch { $timeZone = [TimeZoneInfo]::FindSystemTimeZoneById("Korea Standard Time") }
    [TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow, $timeZone)
}

# 대상 디렉터리를 만든 뒤 JSON을 UTF-8 BOM 없이 저장합니다.
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
if ([string]::IsNullOrWhiteSpace($RunId)) { $RunId = "queue-performance-$($koreaNow.ToString('yyyyMMdd-HHmmss'))" }
$runDirectory = Join-Path $script:RepoRoot "build/k6/runs/$RunId"
$manifestPath = Join-Path $runDirectory "manifest.json"
$usersPath = Join-Path $runDirectory "users.json"
if (Test-Path -LiteralPath $manifestPath) { throw "같은 RunId의 manifest가 이미 있습니다. RunId를 새로 지정해주세요." }

# 테스트 경기 생성
if ($SourceGameId -eq 0) {
    $SourceGameId = [long](Invoke-PerformanceMySql -Scalar -Sql @"
SET time_zone = '+09:00';
SELECT id FROM games
WHERE DATE(game_at) = CURRENT_DATE()
ORDER BY game_at, id LIMIT 1;
"@)
}
if ($SourceGameId -le 0) { throw "KST 오늘의 원본 경기를 찾지 못했습니다. -SourceGameId로 지정해주세요." }

Write-Host "[1/4] 원본 경기 ID ${SourceGameId}에서 오늘 성능 테스트 경기를 복사합니다."
$testGameId = [long](Invoke-PerformanceMySql -Scalar -Sql @"
START TRANSACTION;
SET time_zone = '+09:00';
SET @now_kst = NOW();
SET @booking_close_at = DATE_ADD(@now_kst, INTERVAL $TestDurationMinutes MINUTE);
SET @game_at = DATE_ADD(@booking_close_at, INTERVAL $GameStartLeadMinutes MINUTE);
INSERT INTO games (home_team_id, away_team_id, stadium_id, game_at, booking_open_at, booking_close_at, booking_status, title)
SELECT home_team_id, away_team_id, stadium_id, @game_at, DATE_SUB(@now_kst, INTERVAL 1 MINUTE), @booking_close_at, 'OPEN',
       LEFT(CONCAT('[Queue 성능테스트 $RunId] ', COALESCE(title, '')), 255)
FROM games WHERE id = $SourceGameId;
SET @test_game_id = LAST_INSERT_ID();
COMMIT;
SELECT @test_game_id;
"@)
if ($testGameId -le 0) { throw "원본 경기 ID로 성능 테스트 경기를 만들지 못했습니다. SourceGameId=$SourceGameId" }

$expectedAdminEmail = "queue-admin-$RunId@reseat.local"

$manifest = [ordered]@{
    testType = 'queue-performance'; runId = $RunId; preparedAt = (Get-KoreaNow).ToString('o'); baseUrl = $BaseUrl
    sourceGameId = $SourceGameId; testGameId = $testGameId; gameSeatIds = @()
    adminUser = $null; expectedAdminEmail = $expectedAdminEmail
    expectedUserCount = $UserCount; users = @(); expectedUserEmails = @()
    cleanup = [ordered]@{ appliedAt = $null }
}
Write-JsonFile -Path $manifestPath -Value $manifest

# 임시 관리자 계정 생성
# 이번 RunId의 좌석 재고 API 호출에만 사용하고 최종 정리에서 함께 삭제한다.
$temporaryAdmin = [PSCustomObject]@{
    email = $expectedAdminEmail
    name = "Queue 성능 테스트 관리자"
    nickname = "queue-admin"
    phone = "010-$($koreaNow.ToString('HHmm'))-$([Random]::new().Next(0, 10000).ToString('0000'))"
}
$adminSignupBody = @{ email = $temporaryAdmin.email; password = $TestPassword; name = $temporaryAdmin.name; nickname = $temporaryAdmin.nickname; phone = $temporaryAdmin.phone } | ConvertTo-Json -Compress
Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/signup" -Method Post -ContentType "application/json" -Body $adminSignupBody | Out-Null
$adminRows = @(Invoke-PerformanceMySql -NoHeaders -Sql "SELECT id, email FROM users WHERE email = '$($temporaryAdmin.email)' LIMIT 1;" | ForEach-Object {
    $columns = $_ -split "`t", 2
    [PSCustomObject]@{ userId = [long]$columns[0]; email = $columns[1] }
})

if ($adminRows.Count -ne 1) { throw "성능 테스트 관리자 계정을 준비하지 못했습니다. manifest를 기준으로 생성된 테스트 경기만 정리해주세요." }
$manifest.adminUser = $adminRows[0]
Write-JsonFile -Path $manifestPath -Value $manifest
$adminUserId = [long]$adminRows[0].userId

# 생성한 계정만 ADMIN과 인증 완료 상태로 바꾼다.
Invoke-PerformanceMySql -Sql "UPDATE users SET role = 'ADMIN', status = 'ACTIVE', is_verified = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = $adminUserId;" | Out-Null
$encodedTestPassword = [string](Invoke-PerformanceMySql -Scalar -Sql "SELECT password FROM users WHERE id = $adminUserId;")
if ([string]::IsNullOrWhiteSpace($encodedTestPassword)) { throw "테스트 사용자에게 적용할 암호화 비밀번호를 찾지 못했습니다." }
$adminLoginBody = @{ email = $temporaryAdmin.email; password = $TestPassword } | ConvertTo-Json -Compress
$adminLoginResponse = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $adminLoginBody
if ([string]::IsNullOrWhiteSpace([string]$adminLoginResponse.data.accessToken)) { throw "임시 관리자 Access Token을 발급하지 못했습니다." }

# 새 경기의 좌석 재고는 원본 경기 데이터가 아니라 현재 서버의 PricePolicy로 생성한다.
$adminHeaders = @{ Authorization = "Bearer $($adminLoginResponse.data.accessToken)" }
try {
    $seatOpenResponse = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/games/$testGameId/seats" -Method Post -Headers $adminHeaders
} catch {
    throw "테스트 경기 좌석 재고 오픈 API 호출에 실패했습니다. 테스트 경기 ID: $testGameId"
}

if (-not $seatOpenResponse.success -or [int]$seatOpenResponse.data.gameId -ne $testGameId -or [int]$seatOpenResponse.data.createdCount -le 0) {
    throw "테스트 경기 좌석 재고 오픈 결과가 올바르지 않습니다. 테스트 경기 ID: $testGameId"
}

$gameSeatIds = @(Invoke-PerformanceMySql -NoHeaders -Sql "SELECT id FROM game_seats WHERE game_id = $testGameId ORDER BY id;" | ForEach-Object { [long]$_.ToString().Trim() })
if ($gameSeatIds.Count -ne [int]$seatOpenResponse.data.createdCount) { throw "테스트 경기 좌석 생성 수가 API 응답과 다릅니다. 테스트 경기 ID: $testGameId" }
$manifest.gameSeatIds = $gameSeatIds
Write-JsonFile -Path $manifestPath -Value $manifest

# 테스트 사용자 생성
$phonePrefix = $koreaNow.ToString('mmss')
$usersToCreate = @(1..$UserCount | ForEach-Object {
    $sequence = $_.ToString('0000')
    [PSCustomObject]@{
        email = "queue-user-$RunId-$sequence@reseat.local"
        name = "Queue 성능 테스트 $sequence"
        nickname = "queue-$sequence"
        phone = "010-$phonePrefix-$sequence"
    }
})
$manifest.expectedUserEmails = @($usersToCreate | ForEach-Object { $_.email })
Write-JsonFile -Path $manifestPath -Value $manifest

Write-Host "[2/4] 성능 테스트 사용자 ${UserCount}명을 준비합니다."
$progressInterval = [Math]::Max(1, [int][Math]::Ceiling($UserCount / 10.0))
$signupCount = 0
$signupStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$emailList = (($usersToCreate | ForEach-Object { "'$($_.email)'" }) -join ',')
Write-Host "사용자 생성 진행: 0/${UserCount} (0%)"
try {
    for ($offset = 0; $offset -lt $usersToCreate.Count; $offset += 100) {
        $lastIndex = [Math]::Min($offset + 99, $usersToCreate.Count - 1)
        $batch = @($usersToCreate[$offset..$lastIndex])
        $values = @($batch | ForEach-Object {
            $email = $_.email.Replace("'", "''")
            $name = $_.name.Replace("'", "''")
            $nickname = $_.nickname.Replace("'", "''")
            $phone = $_.phone.Replace("'", "''")
            "('$email', '$encodedTestPassword', '$name', '$nickname', '$phone', 'USER', 'ACTIVE', TRUE)"
        }) -join ",`n"
        Invoke-PerformanceMySql -Sql @"
INSERT INTO users (email, password, name, nickname, phone, role, status, is_verified)
VALUES $values;
"@ | Out-Null
        $signupCount += $batch.Count
        $signupPercent = [int][Math]::Floor($signupCount * 100.0 / $UserCount)
        Write-Host "사용자 생성 진행: ${signupCount}/${UserCount} (${signupPercent}%, 경과 $($signupStopwatch.Elapsed.ToString('hh\:mm\:ss')))"
    }
} finally {
    $signupStopwatch.Stop()
    $createdUsers = @(Invoke-PerformanceMySql -NoHeaders -Sql "SELECT id, email FROM users WHERE email IN ($emailList) ORDER BY id;" | ForEach-Object {
        $columns = $_ -split "`t", 2
        [PSCustomObject]@{ userId = [long]$columns[0]; email = $columns[1] }
    })
    $manifest.users = $createdUsers
    Write-JsonFile -Path $manifestPath -Value $manifest
}
if ($createdUsers.Count -ne $UserCount) { throw "사용자 생성 완료 수가 요청 수와 다릅니다. manifest를 기준으로 생성된 데이터만 정리해주세요." }

$userIdList = ($createdUsers | ForEach-Object { $_.userId }) -join ','
# 실제 본인인증 API는 측정 범위가 아니므로 이번 실행의 생성 사용자 ID만 인증 완료 상태로 맞춘다.
Invoke-PerformanceMySql -Sql "UPDATE users SET status = 'ACTIVE', is_verified = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id IN ($userIdList);" | Out-Null

Write-Host "[3/4] 사용자별 Access Token을 발급합니다."
$loginCount = 0
$loginStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$authenticatedUsers = @()
$loginBatchSize = [Math]::Max($LoginConcurrency, 25)
Write-Host "로그인 진행: 0/${UserCount} (0%)"
try {
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
        if ($failedLogins.Count -gt 0) {
            throw "Access Token 발급에 실패했습니다. 실패 수: $($failedLogins.Count), 첫 사용자: $($failedLogins[0].email), 오류: $($failedLogins[0].error)"
        }
        $successfulLogins = @($batchResults | Select-Object userId, email, accessToken)
        $authenticatedUsers += $successfulLogins
        $loginCount += $successfulLogins.Count
        $loginPercent = [int][Math]::Floor($loginCount * 100.0 / $UserCount)
        Write-Host "로그인 진행: ${loginCount}/${UserCount} (${loginPercent}%, 경과 $($loginStopwatch.Elapsed.ToString('hh\:mm\:ss')))"
    }
} finally {
    $loginStopwatch.Stop()
}
$authenticatedUsers = @($authenticatedUsers | Sort-Object userId)
if ($authenticatedUsers.Count -ne $UserCount) { throw "Access Token 발급 완료 수가 요청 수와 다릅니다." }
Write-JsonFile -Path $usersPath -Value $authenticatedUsers

Write-Host "[4/4] Queue 성능 테스트 데이터 준비가 완료됐습니다."
Write-Host "테스트 경기 ID: $testGameId"
Write-Host "준비된 사용자 수: $($authenticatedUsers.Count)"
Write-Host "manifest: $manifestPath"
Write-Host "k6 사용자 데이터: $usersPath"
Write-Host "Access Token 유효시간은 발급 후 1시간입니다. 준비 완료 후 바로 k6 측정을 시작해주세요."
