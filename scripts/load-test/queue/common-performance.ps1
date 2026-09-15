# Queue 성능 테스트의 준비·초기화·정리 스크립트에서 공통으로 사용하는 함수를 정의합니다.
# 각 스크립트가 저장소 경로를 설정한 뒤 이 파일을 불러옵니다.

# 저장소의 Docker Compose 설정으로 명령을 실행하고 실패하면 오류를 반환합니다.
function Invoke-Compose {
    param([string[]]$Arguments, [string]$InputText)
    $composeArguments = @("compose", "--project-directory", $script:RepoRoot, "-f", $script:ComposeFile) + $Arguments
    if ($PSBoundParameters.ContainsKey("InputText")) { $output = $InputText | & docker @composeArguments 2>&1 }
    else { $output = & docker @composeArguments 2>&1 }
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose 명령 실행에 실패했습니다. $($output -join [Environment]::NewLine)" }
    $output
}

# MySQL 명령을 실행하고 Scalar·헤더 없는 출력 등 호출 목적에 맞게 결과를 반환합니다.
function Invoke-PerformanceMySql {
    param([string]$Sql, [switch]$Scalar, [switch]$NoHeaders)
    $mysql = if ($Scalar -or $NoHeaders) {
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot -Dreseat --batch --raw --skip-column-names'
    } else { 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot -Dreseat --batch --raw' }
    $previousOutputEncoding = $OutputEncoding
    try {
        $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
        $output = Invoke-Compose -Arguments @("exec", "-T", $MySqlService, "sh", "-lc", $mysql) -InputText $Sql
    } finally { $OutputEncoding = $previousOutputEncoding }

    # 조회 결과가 없어도 호출부의 0건 검증으로 이어지도록 빈 문자열을 반환합니다.
    if ($Scalar) {
        $firstLine = $output | Select-Object -First 1
        if ($null -eq $firstLine) { return "" }
        return $firstLine.ToString().Trim()
    }
    $output
}

# 설정값을 환경 변수에서 먼저 찾고 없으면 저장소의 .env에서 읽습니다.
function Get-ComposeSecret {
    param([string]$Name)
    $variable = Get-Item -Path "Env:$Name" -ErrorAction SilentlyContinue
    if ($variable -and -not [string]::IsNullOrWhiteSpace([string]$variable.Value)) { return $variable.Value }
    $line = Get-Content -LiteralPath (Join-Path $script:RepoRoot '.env') | Where-Object { $_ -match "^\s*$Name\s*=" } | Select-Object -First 1
    if (-not $line) { throw "$Name 값을 찾지 못했습니다. 배포 서버의 환경 변수 또는 .env를 확인해주세요." }
    (($line -split "=", 2)[1].Trim().Trim('"').Trim("'"))
}

# Redis 키 명령을 100개씩 나눠 실행하고 영향받은 키 수를 합산합니다.
function Invoke-RedisKeyCommand {
    param([string]$Command, [string[]]$Keys)
    $affectedCount = 0
    for ($offset = 0; $offset -lt $Keys.Count; $offset += 100) {
        $lastIndex = [Math]::Min($offset + 99, $Keys.Count - 1)
        $batch = @($Keys[$offset..$lastIndex])
        $output = Invoke-Compose -Arguments (@('exec', '-T', '-e', "REDISCLI_AUTH=$redisPassword", $RedisService, 'redis-cli', $Command) + $batch)
        $affectedCount += [int](($output | Select-Object -Last 1).ToString().Trim())
    }
    $affectedCount
}

# -Apply에서는 명시한 manifest만 사용하고 dry-run에서는 최근 테스트 manifest를 찾습니다.
function Resolve-ManifestPath {
    if (-not [string]::IsNullOrWhiteSpace($ManifestPath)) { return $ManifestPath }
    if ($Apply) { throw '-Apply를 사용할 때는 dry-run에서 확인한 -ManifestPath를 명시해주세요.' }
    $runsPath = Join-Path $script:RepoRoot 'build/k6/runs'
    if (-not (Test-Path -LiteralPath $runsPath)) { throw 'Queue 성능 테스트 manifest를 찾지 못했습니다. -ManifestPath를 지정해주세요.' }
    $latest = Get-ChildItem -LiteralPath $runsPath -Filter 'manifest.json' -Recurse -File |
        Sort-Object LastWriteTime -Descending |
        Where-Object { (Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json).testType -eq 'queue-performance' } |
        Select-Object -First 1
    if (-not $latest) { throw 'Queue 성능 테스트 manifest를 찾지 못했습니다. -ManifestPath를 지정해주세요.' }
    $latest.FullName
}
