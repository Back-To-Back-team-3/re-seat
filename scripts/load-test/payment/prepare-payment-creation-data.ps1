# 결제 생성 성능 측정에 포함되면 안 되는 사용자·주문·기존 결제 데이터를 실행 전에 준비합니다.
# 이 스크립트는 Docker Compose의 MySQL에 테스트 데이터를 넣고 백엔드 API로 회원가입·로그인을 수행합니다.
# 따라서 01-setup-demo-data.ps1 실행과 Docker Compose·백엔드 기동이 선행되어야 합니다.
#
# 사용자마다 다음 두 데이터 흐름을 독립적으로 준비합니다.
# - 시나리오 A: 결제가 없는 CREATED 주문 + 새 Idempotency-Key
# - 시나리오 B: READY 결제가 있는 CREATED 주문 + 기존 Idempotency-Key
#
# 결과 JSON은 build/k6/payment-creation-users.json에 저장됩니다.
# Access Token이 포함된 build 디렉터리는 Git에서 제외되므로 결과 파일을 커밋하지 않습니다.
#
# Windows: powershell.exe -ExecutionPolicy Bypass -File "./scripts/load-test/payment/prepare-payment-creation-data.ps1" -GameId 117 -UserCount 100
# macOS: pwsh -NoProfile -File "./scripts/load-test/payment/prepare-payment-creation-data.ps1" -GameId 117 -UserCount 100

param(
    [long]$GameId = 117,

    [ValidateRange(1, 2000)]
    [int]$UserCount = 100,

    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl = "http://localhost:8080",

    [ValidateNotNullOrEmpty()]
    [string]$TestPassword = "Test123!"
)

# Docker Compose, MySQL과 Redis 접근 방식은 데모 데이터 스크립트의 공통 함수를 재사용합니다.
. "$PSScriptRoot/../../demo-data/common.ps1"

# 회원가입·로그인 API를 호출할 백엔드 주소가 허용된 형식인지 데이터 변경 전에 확인합니다.
$parsedBaseUrl = $null

if (-not [Uri]::TryCreate($BaseUrl, [UriKind]::Absolute, [ref]$parsedBaseUrl)) {
    throw "BaseUrl은 올바른 절대 URI여야 합니다. 주소: $BaseUrl"
}

$isHttp = $parsedBaseUrl.Scheme -eq [Uri]::UriSchemeHttp
$isHttps = $parsedBaseUrl.Scheme -eq [Uri]::UriSchemeHttps

if (-not $isHttp -and -not $isHttps) {
    throw "BaseUrl은 HTTP 또는 HTTPS 주소여야 합니다. 주소: $BaseUrl"
}

if ($isHttp -and -not $parsedBaseUrl.IsLoopback) {
    throw "HTTP는 로컬 주소에서만 사용할 수 있습니다. 외부 주소는 HTTPS를 사용해주세요. 주소: $BaseUrl"
}

$BaseUrl = $BaseUrl.TrimEnd('/')

# MySQL과 Redis가 실행 중인지 확인한 뒤 백엔드 헬스체크로 애플리케이션 연결 상태까지 확인합니다.
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

# 예약이 참조할 기준 경기만 필요하며 결제 생성 API는 경기의 예매 상태를 확인하지 않습니다.
$preparedGame = [int](Invoke-DemoMySql -Scalar -Sql @"
SELECT COUNT(*)
FROM games
WHERE id = $GameId;
"@)

if ($preparedGame -ne 1) {
    throw "경기 ID ${GameId}를 찾을 수 없습니다."
}

Write-Host "[1/4] 기존 결제 생성 성능 테스트 데이터를 초기화합니다."

# 외래키 제약을 지키기 위해 결제 -> 주문 -> 예약 -> 사용자 순서로 이전 실행 데이터를 삭제합니다.
# payment-load 이메일 접두사만 대상으로 삼아 데모 및 다른 도메인의 테스트 데이터는 유지합니다.
Invoke-DemoMySql -Sql @"
START TRANSACTION;

DELETE p
FROM payments p
JOIN users u ON u.id = p.user_id
WHERE u.email LIKE 'payment-load-%@reseat.local';

DELETE o
FROM orders o
JOIN users u ON u.id = o.user_id
WHERE u.email LIKE 'payment-load-%@reseat.local';

DELETE r
FROM reservations r
JOIN users u ON u.id = r.user_id
WHERE u.email LIKE 'payment-load-%@reseat.local';

DELETE FROM users
WHERE email LIKE 'payment-load-%@reseat.local';

COMMIT;
"@ | Out-Null

Write-Host "[2/4] 결제 생성 성능 테스트 사용자 ${UserCount}명을 준비합니다."

# 각 VU에 독립된 사용자와 JWT를 제공해 사용자·주문 간 데이터 경합이 측정값에 섞이지 않게 합니다.
$usersToCreate = 1..$UserCount | ForEach-Object {
    $sequence = $_.ToString("0000")

    [PSCustomObject]@{
        email    = "payment-load-$sequence@reseat.local"
        name     = "결제 부하 사용자 $sequence"
        nickname = "결제부하$sequence"
        phone    = "010-5678-$sequence"
    }
}

$progressInterval = [Math]::Max(1, [int][Math]::Ceiling($UserCount / 10.0))
$signupCount = 0
$signupUrl = "$BaseUrl/api/v1/auth/signup"

# 비밀번호 해시와 사용자 생성 규칙을 운영 코드와 동일하게 적용하기 위해 회원가입 API를 사용합니다.
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
            -Uri $signupUrl `
            -Method Post `
            -ContentType "application/json" `
            -Body $signupBody | Out-Null
    } catch {
        throw "결제 생성 성능 테스트 사용자 회원가입에 실패했습니다. email=$($user.email)"
    }

    $signupCount++
    if ($signupCount % $progressInterval -eq 0 -or $signupCount -eq $UserCount) {
        $signupPercent = [Math]::Min(100, [int][Math]::Floor(($signupCount / $UserCount) * 100))
        Write-Host "회원가입 진행: ${signupCount}/${UserCount} (${signupPercent}%)"
    }
}

# 본인인증 API는 결제 생성 성능 테스트 범위가 아니므로 테스트 사용자 상태만 직접 활성화합니다.
Invoke-DemoMySql -Sql @"
UPDATE users
SET status = 'ACTIVE',
    is_verified = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE email LIKE 'payment-load-%@reseat.local';
"@ | Out-Null

Write-Host "[3/4] 신규 생성용 주문과 멱등 재요청용 결제를 준비합니다."

# 사용자마다 예약과 주문을 두 개씩 만들어 신규 생성과 멱등 재요청이 서로의 상태를 변경하지 않게 합니다.
# 결제 생성 서비스가 조회하지 않는 좌석·주문 항목은 만들지 않아 다른 도메인의 상태와 준비 비용을 줄입니다.
Invoke-DemoMySql -Sql @"
START TRANSACTION;

INSERT INTO reservations (reservation_no, user_id, game_id, status, hold_expires_at)
SELECT CONCAT('RSV-PAY-NEW-', u.id), u.id, $GameId, 'HOLDING', DATE_ADD(NOW(), INTERVAL 1 DAY)
FROM users u
WHERE u.email LIKE 'payment-load-%@reseat.local';

INSERT INTO reservations (reservation_no, user_id, game_id, status, hold_expires_at)
SELECT CONCAT('RSV-PAY-REPLAY-', u.id), u.id, $GameId, 'HOLDING', DATE_ADD(NOW(), INTERVAL 1 DAY)
FROM users u
WHERE u.email LIKE 'payment-load-%@reseat.local';

INSERT INTO orders (order_no, user_id, reservation_id, total_amount, payment_deadline, status)
SELECT CONCAT('ORD-PAY-NEW-', u.id), u.id, r.id, 10000, DATE_ADD(NOW(), INTERVAL 1 DAY), 'CREATED'
FROM users u
JOIN reservations r ON r.reservation_no = CONCAT('RSV-PAY-NEW-', u.id)
WHERE u.email LIKE 'payment-load-%@reseat.local';

INSERT INTO orders (order_no, user_id, reservation_id, total_amount, payment_deadline, status)
SELECT CONCAT('ORD-PAY-REPLAY-', u.id), u.id, r.id, 10000, DATE_ADD(NOW(), INTERVAL 1 DAY), 'CREATED'
FROM users u
JOIN reservations r ON r.reservation_no = CONCAT('RSV-PAY-REPLAY-', u.id)
WHERE u.email LIKE 'payment-load-%@reseat.local';

INSERT INTO payments (
    payment_no, order_id, user_id, amount, method, status,
    idempotency_key, pg_provider, pg_order_id
)
-- 시나리오 B의 준비 요청이 측정 결과에 포함되지 않도록 READY 결제는 미리 생성합니다.
SELECT
    CONCAT('PAY-LOAD-REPLAY-', u.id),
    o.id,
    u.id,
    o.total_amount,
    NULL,
    'READY',
    CONCAT('payment-load-replay-', u.id),
    'TOSS',
    o.order_no
FROM users u
JOIN orders o ON o.order_no = CONCAT('ORD-PAY-REPLAY-', u.id)
WHERE u.email LIKE 'payment-load-%@reseat.local';

COMMIT;
"@ | Out-Null

# k6가 요청과 응답을 검증하는 데 필요한 ID와 멱등키만 사용자별로 조회합니다.
$fixtureRows = @(Invoke-DemoMySql -NoHeaders -Sql @"
SELECT
    u.id,
    u.email,
    new_order.id,
    replay_order.id,
    replay_payment.id,
    replay_payment.idempotency_key
FROM users u
JOIN orders new_order ON new_order.order_no = CONCAT('ORD-PAY-NEW-', u.id)
JOIN orders replay_order ON replay_order.order_no = CONCAT('ORD-PAY-REPLAY-', u.id)
JOIN payments replay_payment ON replay_payment.order_id = replay_order.id
WHERE u.email LIKE 'payment-load-%@reseat.local'
ORDER BY u.email
LIMIT $UserCount;
"@)

if (@($fixtureRows).Count -ne $UserCount) {
    throw "준비된 결제 생성 테스트 데이터 수가 요청 수와 다릅니다."
}

$fixtures = $fixtureRows | ForEach-Object {
    $columns = $_ -split "`t"

    [PSCustomObject]@{
        userId               = [long]$columns[0]
        email                = $columns[1]
        newOrderId           = [long]$columns[2]
        newIdempotencyKey    = "payment-load-new-$($columns[0])"
        replayOrderId        = [long]$columns[3]
        replayPaymentId      = [long]$columns[4]
        replayIdempotencyKey = $columns[5]
    }
}

# 사용자별 Access Token을 발급해 각 VU가 자신의 주문만 요청하도록 합니다.
$loginCount = 0
$loginUrl = "$BaseUrl/api/v1/auth/login"
$testUsers = @($fixtures | ForEach-Object {
    $fixture = $_
    $loginBody = @{
        email    = $fixture.email
        password = $TestPassword
    } | ConvertTo-Json -Compress

    try {
        $loginResponse = Invoke-RestMethod `
            -Uri $loginUrl `
            -Method Post `
            -ContentType "application/json" `
            -Body $loginBody
    } catch {
        throw "결제 생성 성능 테스트 사용자 로그인에 실패했습니다. email=$($fixture.email)"
    }

    if ([string]::IsNullOrWhiteSpace([String]$loginResponse.data.accessToken)) {
        throw "로그인 응답에 Access Token이 없습니다. email=$($fixture.email)"
    }

    $loginCount++
    if ($loginCount % $progressInterval -eq 0 -or $loginCount -eq $UserCount) {
        $loginPercent = [Math]::Min(100, [int][Math]::Floor(($loginCount / $UserCount) * 100))
        Write-Host "로그인 진행: ${loginCount}/${UserCount} (${loginPercent}%)"
    }

    [PSCustomObject]@{
        userId               = $fixture.userId
        email                = $fixture.email
        accessToken          = $loginResponse.data.accessToken
        newOrderId           = $fixture.newOrderId
        newIdempotencyKey    = $fixture.newIdempotencyKey
        replayOrderId        = $fixture.replayOrderId
        replayPaymentId      = $fixture.replayPaymentId
        replayIdempotencyKey = $fixture.replayIdempotencyKey
    }
})

# JSON에는 인증 정보가 포함되므로 Git에서 제외되는 build/k6 아래에 UTF-8 BOM 없이 저장합니다.
$outputDirectory = Join-Path $script:RepoRoot "build/k6"
$outputFile = Join-Path $outputDirectory "payment-creation-users.json"

[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null

$json = ConvertTo-Json -InputObject @($testUsers) -Depth 3
[System.IO.File]::WriteAllText(
    $outputFile,
    $json,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Host "[4/4] 결제 생성 성능 테스트 데이터 준비가 완료됐습니다."
Write-Host "대상 경기 ID: $GameId"
Write-Host "준비된 사용자 수: $(@($testUsers).Count)"
Write-Host "k6 사용자 데이터: $outputFile"
Write-Host "Access Token의 유효시간은 1시간이므로 만료되면 이 스크립트를 다시 실행해주세요."
