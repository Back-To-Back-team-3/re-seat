# 공통 도구: 다른 데모 스크립트가 불러오는 Docker Compose·MySQL·Redis 함수이며 직접 실행하지 않습니다.

$ErrorActionPreference = "Stop"

$script:RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$script:ComposeFile = Join-Path $script:RepoRoot "docker-compose.yml"
$script:MySqlService = "mysql-db"
$script:RedisService = "redis"
$script:DemoGameIds = @(106, 111, 117, 121, 126, 131)
$script:DemoGameIdList = $script:DemoGameIds -join ","
$script:DemoProvider = "TEAM_DEMO"
$script:DemoOwnerProviderId = "demo-owner"
$script:DemoOwnerEmail = "demo-owner@reseat.local"

function Get-DemoComposeArguments {
    param([string[]]$Arguments)

    return @(
        "compose",
        "--project-directory", $script:RepoRoot,
        "-f", $script:ComposeFile
    ) + $Arguments
}

function Invoke-DemoDocker {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [string]$InputText
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        # Docker Compose 경고가 Windows PowerShell에서 terminating error로 변환되지 않도록 한다.
        $ErrorActionPreference = "Continue"
        if ($PSBoundParameters.ContainsKey("InputText")) {
            $output = $InputText | & docker @Arguments 2>$null
        } else {
            $output = & docker @Arguments 2>$null
        }
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    return [PSCustomObject]@{
        Output = $output
        ExitCode = $exitCode
    }
}

function Assert-DemoServices {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker CLI를 찾을 수 없습니다. Docker Desktop을 설치하고 실행해주세요."
    }

    if (-not (Test-Path -LiteralPath $script:ComposeFile)) {
        throw "저장소 루트에서 docker-compose.yml을 찾을 수 없습니다."
    }

    foreach ($serviceName in @($script:MySqlService, $script:RedisService)) {
        $composeArguments = Get-DemoComposeArguments -Arguments @("ps", "-q", $serviceName)
        $composeResult = Invoke-DemoDocker -Arguments $composeArguments
        $containerId = $composeResult.Output | Select-Object -First 1
        if ($composeResult.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
            throw "Docker Compose 서비스 '${serviceName}'이 실행 중이 아닙니다. 저장소 루트에서 docker compose up -d를 실행해주세요."
        }

        $inspectResult = Invoke-DemoDocker -Arguments @(
            "inspect", "--format", "{{.State.Running}}", $containerId
        )
        if ($inspectResult.ExitCode -ne 0 -or $inspectResult.Output -ne "true") {
            throw "Docker Compose 서비스 '${serviceName}'이 실행 중이 아닙니다."
        }
    }
}

function Invoke-DemoMySql {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Sql,
        [switch]$Scalar,
        [switch]$NoHeaders
    )

    $mysqlCommand = if ($Scalar -or $NoHeaders) {
        'mysql --default-character-set=utf8mb4 -uroot -p${MYSQL_ROOT_PASSWORD} -Dreseat --batch --raw --skip-column-names'
    } else {
        'mysql --default-character-set=utf8mb4 -uroot -p${MYSQL_ROOT_PASSWORD} -Dreseat --batch --raw'
    }

    $previousOutputEncoding = $OutputEncoding
    try {
        # Windows PowerShell의 기본 BOM이 SQL 첫 토큰에 섞이지 않도록 UTF-8 BOM 없이 전달한다.
        $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
        $composeArguments = Get-DemoComposeArguments -Arguments @(
            "exec", "-T", $script:MySqlService, "sh", "-lc", $mysqlCommand
        )
        $dockerResult = Invoke-DemoDocker -Arguments $composeArguments -InputText $Sql
        $output = $dockerResult.Output
    } finally {
        $OutputEncoding = $previousOutputEncoding
    }
    if ($dockerResult.ExitCode -ne 0) {
        throw "MySQL 명령 실행에 실패했습니다."
    }

    if ($Scalar) {
        return ($output | Select-Object -First 1).Trim()
    }
    return $output
}

function Clear-DemoRedisQueues {
    param([long[]]$GameIds = $script:DemoGameIds)

    $keys = $GameIds | ForEach-Object { "queue:game:$_" }
    if ($keys.Count -gt 0) {
        Invoke-DemoRedis -Arguments (@("DEL") + $keys) | Out-Null
    }
}

function Get-DemoRedisPassword {
    if (-not [string]::IsNullOrWhiteSpace($env:REDIS_PASSWORD)) {
        return $env:REDIS_PASSWORD
    }

    $envPath = Join-Path $script:RepoRoot ".env"
    if (-not (Test-Path -LiteralPath $envPath)) {
        throw ".env 파일을 찾을 수 없어 Redis 인증 정보를 읽지 못했습니다."
    }

    $passwordLine = Get-Content -LiteralPath $envPath | Where-Object {
        $_ -match '^\s*REDIS_PASSWORD\s*='
    } | Select-Object -First 1

    if (-not $passwordLine) {
        throw ".env에 REDIS_PASSWORD가 없습니다."
    }

    $password = ($passwordLine -split '=', 2)[1].Trim().Trim('"').Trim("'")
    if ([string]::IsNullOrWhiteSpace($password)) {
        throw ".env의 REDIS_PASSWORD가 비어 있습니다."
    }
    return $password
}

function Invoke-DemoRedis {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $password = Get-DemoRedisPassword
    $composeArguments = Get-DemoComposeArguments -Arguments @(
        "exec", "-T", "-e", "REDISCLI_AUTH=$password", $script:RedisService, "redis-cli"
    )
    $dockerResult = Invoke-DemoDocker -Arguments ($composeArguments + $Arguments)
    $output = $dockerResult.Output
    $failedResponse = @($output) | Where-Object { $_ -match 'NOAUTH|WRONGPASS|^\(error\)' }
    if ($dockerResult.ExitCode -ne 0 -or $failedResponse) {
        throw "Redis 명령 실행에 실패했습니다. 인증 설정과 컨테이너 상태를 확인해주세요."
    }
    return $output
}

function Get-DemoGameSummary {
    Invoke-DemoMySql -Sql @"
SELECT id, title, game_at, booking_status
FROM games
WHERE id IN ($script:DemoGameIdList)
ORDER BY game_at, id;
"@
}
