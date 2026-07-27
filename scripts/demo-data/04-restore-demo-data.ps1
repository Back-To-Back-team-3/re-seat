# 4번 최종 복구: 1번 실행 전에 백업한 6경기와 연관 데이터를 복원하고 데모 사용자를 제거합니다.
# Windows: powershell.exe -ExecutionPolicy Bypass -File "./scripts/demo-data/04-restore-demo-data.ps1"
# macOS: pwsh -NoProfile -File "./scripts/demo-data/04-restore-demo-data.ps1"
# 이 스크립트까지 실행하면 데모 작업 한 사이클이 종료됩니다.

. "$PSScriptRoot/common.ps1"

Assert-DemoServices

$backupExists = [int](Invoke-DemoMySql -Scalar -Sql @"
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = 'reseat_demo_backup'
  AND table_name = 'metadata';
"@)

if ($backupExists -ne 1) {
    throw "복원할 데모 백업이 없습니다. 01-setup-demo-data.ps1 실행 여부를 확인해주세요."
}

$calendarBackupExists = [int](Invoke-DemoMySql -Scalar -Sql @"
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = 'reseat_demo_backup'
  AND table_name = 'calendar_conflicting_games';
"@)

$restoreConflictingGames = if ($calendarBackupExists -eq 1) {
    @"
UPDATE games g
JOIN reseat_demo_backup.calendar_conflicting_games b ON b.id = g.id
SET
    g.home_team_id = b.home_team_id,
    g.away_team_id = b.away_team_id,
    g.stadium_id = b.stadium_id,
    g.game_at = b.game_at,
    g.booking_open_at = b.booking_open_at,
    g.booking_close_at = b.booking_close_at,
    g.booking_status = b.booking_status,
    g.title = b.title,
    g.created_at = b.created_at,
    g.updated_at = b.updated_at;
"@
} else {
    ""
}

Write-Host "[1/3] 현재 데모 대기열을 제거합니다."
Clear-DemoRedisQueues

Write-Host "[2/3] 준비 스크립트가 바꾼 6경기와 연관 데이터를 원래 상태로 복원합니다."
Invoke-DemoMySql -Sql @"
START TRANSACTION;
SET @demo_games = '$script:DemoGameIdList';

DELETE prt FROM payment_recovery_tasks prt
JOIN payments p ON p.id = prt.payment_id
JOIN orders o ON o.id = p.order_id
JOIN reservations r ON r.id = o.reservation_id
WHERE FIND_IN_SET(r.game_id, @demo_games);

DELETE FROM tickets WHERE FIND_IN_SET(game_id, @demo_games);

DELETE p FROM payments p
JOIN orders o ON o.id = p.order_id
JOIN reservations r ON r.id = o.reservation_id
WHERE FIND_IN_SET(r.game_id, @demo_games);

DELETE oi FROM order_items oi
JOIN orders o ON o.id = oi.order_id
JOIN reservations r ON r.id = o.reservation_id
WHERE FIND_IN_SET(r.game_id, @demo_games);

DELETE o FROM orders o
JOIN reservations r ON r.id = o.reservation_id
WHERE FIND_IN_SET(r.game_id, @demo_games);

DELETE rs FROM reservation_seats rs
JOIN reservations r ON r.id = rs.reservation_id
WHERE FIND_IN_SET(r.game_id, @demo_games);

DELETE FROM reservations WHERE FIND_IN_SET(game_id, @demo_games);
DELETE FROM admission_tokens WHERE FIND_IN_SET(game_id, @demo_games);
DELETE FROM queue_entry_histories WHERE FIND_IN_SET(game_id, @demo_games);
DELETE FROM game_seats WHERE FIND_IN_SET(game_id, @demo_games);

UPDATE games g
JOIN reseat_demo_backup.games b ON b.id = g.id
SET
    g.home_team_id = b.home_team_id,
    g.away_team_id = b.away_team_id,
    g.stadium_id = b.stadium_id,
    g.game_at = b.game_at,
    g.booking_open_at = b.booking_open_at,
    g.booking_close_at = b.booking_close_at,
    g.booking_status = b.booking_status,
    g.title = b.title,
    g.created_at = b.created_at,
    g.updated_at = b.updated_at;

$restoreConflictingGames

INSERT INTO game_seats SELECT * FROM reseat_demo_backup.game_seats;
INSERT INTO queue_entry_histories SELECT * FROM reseat_demo_backup.queue_entry_histories;
INSERT INTO admission_tokens SELECT * FROM reseat_demo_backup.admission_tokens;
INSERT INTO reservations SELECT * FROM reseat_demo_backup.reservations;
INSERT INTO reservation_seats SELECT * FROM reseat_demo_backup.reservation_seats;
INSERT INTO orders SELECT * FROM reseat_demo_backup.orders;
INSERT INTO order_items SELECT * FROM reseat_demo_backup.order_items;
INSERT INTO payments SELECT * FROM reseat_demo_backup.payments;
INSERT INTO payment_recovery_tasks SELECT * FROM reseat_demo_backup.payment_recovery_tasks;
INSERT INTO tickets SELECT * FROM reseat_demo_backup.tickets;

UPDATE users u
JOIN reseat_demo_backup.users b ON b.id = u.id
SET
    u.email = b.email,
    u.password = b.password,
    u.name = b.name,
    u.nickname = b.nickname,
    u.phone = b.phone,
    u.role = b.role,
    u.status = b.status,
    u.ci = b.ci,
    u.is_verified = b.is_verified,
    u.provider = b.provider,
    u.provider_id = b.provider_id,
    u.created_at = b.created_at,
    u.updated_at = b.updated_at;

DELETE FROM users
-- 이전 로컬 스크립트로 생성한 데이터도 함께 정리한다.
WHERE provider IN ('$script:DemoProvider', 'LOCAL_DEMO')
  AND provider_id LIKE 'queue-demo-%';

DELETE FROM users
WHERE provider IN ('$script:DemoProvider', 'LOCAL_DEMO')
  AND provider_id = '$script:DemoOwnerProviderId'
  AND id NOT IN (SELECT id FROM reseat_demo_backup.users);

COMMIT;
"@ | Out-Null

Invoke-DemoMySql -Sql "DROP DATABASE reseat_demo_backup;" | Out-Null

Write-Host "[3/3] 복원이 완료되었습니다."
Get-DemoGameSummary | Format-Table -AutoSize
