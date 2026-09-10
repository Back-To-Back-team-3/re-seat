# 임시 우회 스크립트: 기존 setup 스크립트가 생성 컬럼을 SELECT *로 복사하는 동안만
# 백업 테이블의 active_seat_key를 일반 컬럼으로 바꿨다가 실행 후 원래대로 복원합니다.
# 원본 스크립트가 명시적 컬럼 목록을 사용하도록 수정되면 이 파일은 제거합니다.

. "$PSScriptRoot/common.ps1"

$originalSetupScript = Join-Path $PSScriptRoot "01-setup-demo-data.ps1"
$activeSeatKeyIsGenerated = [int](Invoke-DemoMySql -Scalar -Sql @"
SELECT COUNT(*)
FROM information_schema.columns
WHERE table_schema = 'reseat_demo_backup'
  AND table_name = 'tickets'
  AND column_name = 'active_seat_key'
  AND extra LIKE '%GENERATED%';
"@)

if ($activeSeatKeyIsGenerated -eq 0) {
    throw "임시 우회 대상인 reseat_demo_backup.tickets.active_seat_key 생성 컬럼을 찾지 못했습니다."
}

try {
    Write-Host "[임시 처리] 백업 티켓의 생성 컬럼을 일반 컬럼으로 전환합니다."
    Invoke-DemoMySql -Sql @"
ALTER TABLE reseat_demo_backup.tickets
MODIFY COLUMN active_seat_key BIGINT NULL;
"@ | Out-Null

    & $originalSetupScript
} finally {
    Write-Host "[임시 처리] 백업 티켓의 생성 컬럼을 원래 정의로 복원합니다."
    Invoke-DemoMySql -Sql @"
ALTER TABLE reseat_demo_backup.tickets
MODIFY COLUMN active_seat_key BIGINT
GENERATED ALWAYS AS (IF(status = 'REFUNDED', NULL, game_seat_id)) STORED;
"@ | Out-Null
}
