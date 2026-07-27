# 1번 필수 준비: 기존 데이터를 백업하고 상태별 테스트가 가능한 6경기·좌석·주문·티켓 데이터를 만듭니다.
# Windows: powershell.exe -ExecutionPolicy Bypass -File "./scripts/demo-data/01-setup-demo-data.ps1"
# macOS: pwsh -NoProfile -File "./scripts/demo-data/01-setup-demo-data.ps1"
# 날짜 배치: 6경기를 모두 스크립트 실행일인 오늘로 설정합니다.
# 다음 순서: 대기열을 볼 때만 02-seed-demo-queue.ps1을 실행합니다.

. "$PSScriptRoot/common.ps1"

Assert-DemoServices

$existingGameCount = [int](Invoke-DemoMySql -Scalar -Sql @"
SELECT COUNT(*)
FROM games
WHERE id IN ($script:DemoGameIdList);
"@)

if ($existingGameCount -ne $script:DemoGameIds.Count) {
    throw "기존 경기 ID 106, 111, 117, 121, 126, 131을 모두 찾을 수 없습니다. 현재 DB 마이그레이션 상태를 확인해주세요."
}

$backupExists = [int](Invoke-DemoMySql -Scalar -Sql @"
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = 'reseat_demo_backup'
  AND table_name = 'metadata';
"@)

if ($backupExists -eq 0) {
    Write-Host "[1/5] 기존 경기 6개와 연관 데이터를 백업합니다."
    Invoke-DemoMySql -Sql @"
CREATE DATABASE IF NOT EXISTS reseat_demo_backup CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE reseat_demo_backup.metadata (
    id INT PRIMARY KEY,
    game_ids VARCHAR(100) NOT NULL,
    owner_user_id BIGINT NULL,
    created_at DATETIME NOT NULL
);

SET @demo_owner_id = (
    SELECT id
    FROM users
    WHERE provider = '$script:DemoProvider'
      AND provider_id = '$script:DemoOwnerProviderId'
    LIMIT 1
);

INSERT INTO reseat_demo_backup.metadata (id, game_ids, owner_user_id, created_at)
VALUES (1, '$script:DemoGameIdList', @demo_owner_id, NOW());

CREATE TABLE reseat_demo_backup.games LIKE reseat.games;
INSERT INTO reseat_demo_backup.games SELECT * FROM reseat.games WHERE id IN ($script:DemoGameIdList);

CREATE TABLE reseat_demo_backup.users LIKE reseat.users;
INSERT INTO reseat_demo_backup.users SELECT * FROM reseat.users WHERE id = @demo_owner_id;

CREATE TABLE reseat_demo_backup.game_seats LIKE reseat.game_seats;
INSERT INTO reseat_demo_backup.game_seats SELECT * FROM reseat.game_seats WHERE game_id IN ($script:DemoGameIdList);

CREATE TABLE reseat_demo_backup.queue_entry_histories LIKE reseat.queue_entry_histories;
INSERT INTO reseat_demo_backup.queue_entry_histories SELECT * FROM reseat.queue_entry_histories WHERE game_id IN ($script:DemoGameIdList);

CREATE TABLE reseat_demo_backup.admission_tokens LIKE reseat.admission_tokens;
INSERT INTO reseat_demo_backup.admission_tokens SELECT * FROM reseat.admission_tokens WHERE game_id IN ($script:DemoGameIdList);

CREATE TABLE reseat_demo_backup.reservations LIKE reseat.reservations;
INSERT INTO reseat_demo_backup.reservations SELECT * FROM reseat.reservations WHERE game_id IN ($script:DemoGameIdList);

CREATE TABLE reseat_demo_backup.reservation_seats LIKE reseat.reservation_seats;
INSERT INTO reseat_demo_backup.reservation_seats
SELECT rs.* FROM reseat.reservation_seats rs
JOIN reseat.reservations r ON r.id = rs.reservation_id
WHERE r.game_id IN ($script:DemoGameIdList);

CREATE TABLE reseat_demo_backup.orders LIKE reseat.orders;
INSERT INTO reseat_demo_backup.orders
SELECT o.* FROM reseat.orders o
JOIN reseat.reservations r ON r.id = o.reservation_id
WHERE r.game_id IN ($script:DemoGameIdList);

CREATE TABLE reseat_demo_backup.order_items LIKE reseat.order_items;
INSERT INTO reseat_demo_backup.order_items
SELECT oi.* FROM reseat.order_items oi
JOIN reseat.orders o ON o.id = oi.order_id
JOIN reseat.reservations r ON r.id = o.reservation_id
WHERE r.game_id IN ($script:DemoGameIdList);

CREATE TABLE reseat_demo_backup.payments LIKE reseat.payments;
INSERT INTO reseat_demo_backup.payments
SELECT p.* FROM reseat.payments p
JOIN reseat.orders o ON o.id = p.order_id
JOIN reseat.reservations r ON r.id = o.reservation_id
WHERE r.game_id IN ($script:DemoGameIdList);

CREATE TABLE reseat_demo_backup.payment_recovery_tasks LIKE reseat.payment_recovery_tasks;
INSERT INTO reseat_demo_backup.payment_recovery_tasks
SELECT prt.* FROM reseat.payment_recovery_tasks prt
JOIN reseat.payments p ON p.id = prt.payment_id
JOIN reseat.orders o ON o.id = p.order_id
JOIN reseat.reservations r ON r.id = o.reservation_id
WHERE r.game_id IN ($script:DemoGameIdList);

CREATE TABLE reseat_demo_backup.tickets LIKE reseat.tickets;
INSERT INTO reseat_demo_backup.tickets SELECT * FROM reseat.tickets WHERE game_id IN ($script:DemoGameIdList);
"@ | Out-Null
} else {
    Write-Host "[1/5] 기존 백업을 유지하고 새로 포함한 경기 106의 원본 데이터를 보강합니다."
    Invoke-DemoMySql -Sql @"
INSERT IGNORE INTO reseat_demo_backup.games SELECT * FROM reseat.games WHERE id = 106;
INSERT IGNORE INTO reseat_demo_backup.game_seats SELECT * FROM reseat.game_seats WHERE game_id = 106;
INSERT IGNORE INTO reseat_demo_backup.queue_entry_histories SELECT * FROM reseat.queue_entry_histories WHERE game_id = 106;
INSERT IGNORE INTO reseat_demo_backup.admission_tokens SELECT * FROM reseat.admission_tokens WHERE game_id = 106;
INSERT IGNORE INTO reseat_demo_backup.reservations SELECT * FROM reseat.reservations WHERE game_id = 106;

INSERT IGNORE INTO reseat_demo_backup.reservation_seats
SELECT rs.* FROM reseat.reservation_seats rs
JOIN reseat.reservations r ON r.id = rs.reservation_id
WHERE r.game_id = 106;

INSERT IGNORE INTO reseat_demo_backup.orders
SELECT o.* FROM reseat.orders o
JOIN reseat.reservations r ON r.id = o.reservation_id
WHERE r.game_id = 106;

INSERT IGNORE INTO reseat_demo_backup.order_items
SELECT oi.* FROM reseat.order_items oi
JOIN reseat.orders o ON o.id = oi.order_id
JOIN reseat.reservations r ON r.id = o.reservation_id
WHERE r.game_id = 106;

INSERT IGNORE INTO reseat_demo_backup.payments
SELECT p.* FROM reseat.payments p
JOIN reseat.orders o ON o.id = p.order_id
JOIN reseat.reservations r ON r.id = o.reservation_id
WHERE r.game_id = 106;

INSERT IGNORE INTO reseat_demo_backup.payment_recovery_tasks
SELECT prt.* FROM reseat.payment_recovery_tasks prt
JOIN reseat.payments p ON p.id = prt.payment_id
JOIN reseat.orders o ON o.id = p.order_id
JOIN reseat.reservations r ON r.id = o.reservation_id
WHERE r.game_id = 106;

INSERT IGNORE INTO reseat_demo_backup.tickets SELECT * FROM reseat.tickets WHERE game_id = 106;
UPDATE reseat_demo_backup.metadata SET game_ids = '$script:DemoGameIdList' WHERE id = 1;
"@ | Out-Null
}

$calendarBackupExists = [int](Invoke-DemoMySql -Scalar -Sql @"
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = 'reseat_demo_backup'
  AND table_name = 'calendar_conflicting_games';
"@)

if ($calendarBackupExists -eq 0) {
    Invoke-DemoMySql -Sql @"
CREATE TABLE reseat_demo_backup.calendar_conflicting_games LIKE reseat.games;
INSERT INTO reseat_demo_backup.calendar_conflicting_games
SELECT *
FROM reseat.games
WHERE id NOT IN ($script:DemoGameIdList)
  AND DATE(DATE_ADD(game_at, INTERVAL 9 HOUR)) = DATE(DATE_ADD(UTC_TIMESTAMP(), INTERVAL 9 HOUR));
"@ | Out-Null
}

Write-Host "[2/5] 오늘과 겹치는 기존 경기를 다음 날로 임시 이동합니다."
Invoke-DemoMySql -Sql @"
UPDATE games g
JOIN reseat_demo_backup.calendar_conflicting_games b ON b.id = g.id
SET
    g.game_at = DATE_ADD(b.game_at, INTERVAL 1 DAY),
    g.booking_open_at = DATE_ADD(b.booking_open_at, INTERVAL 1 DAY),
    g.booking_close_at = DATE_ADD(b.booking_close_at, INTERVAL 1 DAY);
"@ | Out-Null

Write-Host "[3/5] 선택 경기의 기존 연관 데이터를 정리합니다."
Invoke-DemoMySql -Sql @"
SET @demo_games = '$script:DemoGameIdList';

DELETE prt FROM payment_recovery_tasks prt
JOIN payments p ON p.id = prt.payment_id
JOIN orders o ON o.id = p.order_id
JOIN reservations r ON r.id = o.reservation_id
WHERE FIND_IN_SET(r.game_id, @demo_games);

DELETE t FROM tickets t WHERE FIND_IN_SET(t.game_id, @demo_games);

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
"@ | Out-Null

Write-Host "[4/5] 동적 일정, 좌석 재고와 연관 상태를 준비합니다."
Invoke-DemoMySql -Sql @"
START TRANSACTION;
SET time_zone = '+00:00';
SET @kst_today_utc = DATE_SUB(DATE(DATE_ADD(UTC_TIMESTAMP(), INTERVAL 9 HOUR)), INTERVAL 9 HOUR);

SET @demo_user_id = (
    SELECT id
    FROM users
    WHERE provider = '$script:DemoProvider'
      AND provider_id = '$script:DemoOwnerProviderId'
    LIMIT 1
);

INSERT INTO users (
    email, password, name, nickname, phone, role, status,
    ci, is_verified, provider, provider_id
)
SELECT
    '$script:DemoOwnerEmail', NULL, '데모 사용자', '데모', NULL, 'USER', 'ACTIVE',
    NULL, TRUE, '$script:DemoProvider', '$script:DemoOwnerProviderId'
WHERE @demo_user_id IS NULL;

SET @demo_user_id = COALESCE(@demo_user_id, LAST_INSERT_ID());

UPDATE games
SET game_at = DATE_ADD(@kst_today_utc, INTERVAL 17 HOUR),
    booking_open_at = LEAST(
        DATE_ADD(UTC_TIMESTAMP(), INTERVAL 2 HOUR),
        DATE_ADD(@kst_today_utc, INTERVAL 15 HOUR)
    ),
    booking_close_at = DATE_ADD(@kst_today_utc, INTERVAL 16 HOUR),
    booking_status = 'SCHEDULED',
    title = CONCAT('[예매 예정] ', (SELECT title FROM reseat_demo_backup.games WHERE id = 106))
WHERE id = 106;

UPDATE games
SET game_at = DATE_ADD(@kst_today_utc, INTERVAL 18 HOUR) + INTERVAL 30 MINUTE,
    booking_open_at = DATE_SUB(NOW(), INTERVAL 1 DAY),
    booking_close_at = DATE_ADD(@kst_today_utc, INTERVAL 18 HOUR),
    booking_status = 'OPEN',
    title = CONCAT('[일반 예매] ', (SELECT title FROM reseat_demo_backup.games WHERE id = 111))
WHERE id = 111;

UPDATE games
SET game_at = DATE_ADD(@kst_today_utc, INTERVAL 18 HOUR) + INTERVAL 30 MINUTE,
    booking_open_at = DATE_SUB(NOW(), INTERVAL 1 DAY),
    booking_close_at = DATE_ADD(@kst_today_utc, INTERVAL 18 HOUR),
    booking_status = 'OPEN',
    title = CONCAT('[대기열 체험] ', (SELECT title FROM reseat_demo_backup.games WHERE id = 117))
WHERE id = 117;

UPDATE games
SET game_at = DATE_ADD(@kst_today_utc, INTERVAL 18 HOUR) + INTERVAL 30 MINUTE,
    booking_open_at = DATE_SUB(NOW(), INTERVAL 1 DAY),
    booking_close_at = DATE_ADD(@kst_today_utc, INTERVAL 18 HOUR),
    booking_status = 'OPEN',
    title = CONCAT('[좌석 상태 혼합] ', (SELECT title FROM reseat_demo_backup.games WHERE id = 121))
WHERE id = 121;

UPDATE games
SET game_at = DATE_ADD(@kst_today_utc, INTERVAL 18 HOUR) + INTERVAL 30 MINUTE,
    booking_open_at = DATE_SUB(NOW(), INTERVAL 1 DAY),
    booking_close_at = DATE_SUB(NOW(), INTERVAL 1 HOUR),
    booking_status = 'CLOSED',
    title = CONCAT('[예매 종료] ', (SELECT title FROM reseat_demo_backup.games WHERE id = 126))
WHERE id = 126;

UPDATE games
SET game_at = DATE_ADD(@kst_today_utc, INTERVAL 18 HOUR) + INTERVAL 30 MINUTE,
    booking_open_at = DATE_SUB(NOW(), INTERVAL 1 DAY),
    booking_close_at = DATE_ADD(@kst_today_utc, INTERVAL 18 HOUR),
    booking_status = 'CANCELLED',
    title = CONCAT('[경기 취소] ', (SELECT title FROM reseat_demo_backup.games WHERE id = 131))
WHERE id = 131;

INSERT INTO game_seats (game_id, seat_id, price, status, version)
SELECT g.id, s.id, z.base_price, 'AVAILABLE', 0
FROM games g
JOIN seats s ON s.stadium_id = g.stadium_id AND s.status = 'ACTIVE'
JOIN seat_zones z ON z.id = s.zone_id
WHERE g.id IN ($script:DemoGameIdList);

UPDATE game_seats
SET status = 'BLOCKED'
WHERE id IN (
    SELECT id FROM (
        SELECT id FROM game_seats WHERE game_id = 121 ORDER BY id LIMIT 8
    ) blocked_seats
);

SET @mixed_seat_1 = (SELECT id FROM game_seats WHERE game_id = 121 AND status = 'AVAILABLE' ORDER BY id LIMIT 1);
SET @mixed_seat_2 = (SELECT id FROM game_seats WHERE game_id = 121 AND status = 'AVAILABLE' ORDER BY id LIMIT 1 OFFSET 1);
UPDATE game_seats SET status = 'HELD', hold_expires_at = DATE_ADD(NOW(), INTERVAL 1 DAY) WHERE id IN (@mixed_seat_1, @mixed_seat_2);

INSERT INTO reservations (reservation_no, user_id, game_id, status, hold_expires_at)
VALUES ('RSV-DEMO-MIXED', @demo_user_id, 121, 'HOLDING', DATE_ADD(NOW(), INTERVAL 1 DAY));
SET @mixed_reservation_id = LAST_INSERT_ID();
INSERT INTO reservation_seats (reservation_id, game_seat_id, price)
SELECT @mixed_reservation_id, id, price FROM game_seats WHERE id IN (@mixed_seat_1, @mixed_seat_2);

SET @ready_seat_1 = (SELECT id FROM game_seats WHERE game_id = 126 ORDER BY id LIMIT 1);
SET @ready_seat_2 = (SELECT id FROM game_seats WHERE game_id = 126 ORDER BY id LIMIT 1 OFFSET 1);
UPDATE game_seats SET status = 'HELD', hold_expires_at = DATE_ADD(NOW(), INTERVAL 1 DAY) WHERE id IN (@ready_seat_1, @ready_seat_2);

INSERT INTO reservations (reservation_no, user_id, game_id, status, hold_expires_at)
VALUES ('RSV-DEMO-READY', @demo_user_id, 126, 'HOLDING', DATE_ADD(NOW(), INTERVAL 1 DAY));
SET @ready_reservation_id = LAST_INSERT_ID();
INSERT INTO reservation_seats (reservation_id, game_seat_id, price)
SELECT @ready_reservation_id, id, price FROM game_seats WHERE id IN (@ready_seat_1, @ready_seat_2);

SET @ready_total = (SELECT SUM(price) FROM game_seats WHERE id IN (@ready_seat_1, @ready_seat_2));
INSERT INTO orders (order_no, user_id, reservation_id, total_amount, payment_deadline, status)
VALUES ('ORD-DEMO-READY', @demo_user_id, @ready_reservation_id, @ready_total, DATE_ADD(NOW(), INTERVAL 1 DAY), 'CREATED');
SET @ready_order_id = LAST_INSERT_ID();
INSERT INTO order_items (order_id, game_seat_id, price)
SELECT @ready_order_id, id, price FROM game_seats WHERE id IN (@ready_seat_1, @ready_seat_2);
INSERT INTO payments (
    payment_no, order_id, user_id, amount, method, status,
    idempotency_key, pg_provider, pg_order_id
)
VALUES (
    'PAY-DEMO-READY', @ready_order_id, @demo_user_id, @ready_total, NULL, 'READY',
    'demo-ready-idempotency-key', 'TOSS', 'ORD-DEMO-READY'
);

SET @paid_seat_1 = (SELECT id FROM game_seats WHERE game_id = 131 ORDER BY id LIMIT 1);
SET @paid_seat_2 = (SELECT id FROM game_seats WHERE game_id = 131 ORDER BY id LIMIT 1 OFFSET 1);
UPDATE game_seats SET status = 'SOLD', sold_at = NOW() WHERE id IN (@paid_seat_1, @paid_seat_2);

INSERT INTO reservations (reservation_no, user_id, game_id, status, hold_expires_at)
VALUES ('RSV-DEMO-PAID', @demo_user_id, 131, 'CONFIRMED', DATE_ADD(NOW(), INTERVAL 1 DAY));
SET @paid_reservation_id = LAST_INSERT_ID();
INSERT INTO reservation_seats (reservation_id, game_seat_id, price)
SELECT @paid_reservation_id, id, price FROM game_seats WHERE id IN (@paid_seat_1, @paid_seat_2);

SET @paid_total = (SELECT SUM(price) FROM game_seats WHERE id IN (@paid_seat_1, @paid_seat_2));
INSERT INTO orders (order_no, user_id, reservation_id, total_amount, payment_deadline, status)
VALUES ('ORD-DEMO-PAID', @demo_user_id, @paid_reservation_id, @paid_total, DATE_ADD(NOW(), INTERVAL 1 DAY), 'PAID');
SET @paid_order_id = LAST_INSERT_ID();
INSERT INTO order_items (order_id, game_seat_id, price)
SELECT @paid_order_id, id, price FROM game_seats WHERE id IN (@paid_seat_1, @paid_seat_2) ORDER BY id;

INSERT INTO payments (
    payment_no, order_id, user_id, amount, method, status,
    idempotency_key, pg_provider, pg_order_id, pg_payment_key, approved_at
)
VALUES (
    'PAY-DEMO-PAID', @paid_order_id, @demo_user_id, @paid_total, '카드', 'APPROVED',
    'demo-paid-idempotency-key', 'TOSS', 'ORD-DEMO-PAID', 'demo-payment-key', NOW()
);

INSERT INTO tickets (ticket_no, user_id, order_item_id, game_id, game_seat_id, status, qr_token, issued_at)
SELECT
    CONCAT('TKT-DEMO-', LPAD(ROW_NUMBER() OVER (ORDER BY oi.id), 2, '0')),
    @demo_user_id,
    oi.id,
    131,
    oi.game_seat_id,
    'ISSUED',
    CONCAT('qr-demo-', oi.id),
    NOW()
FROM order_items oi
WHERE oi.order_id = @paid_order_id;

COMMIT;
"@ | Out-Null

Clear-DemoRedisQueues

Write-Host "[5/5] 준비가 완료되었습니다."
Get-DemoGameSummary | Format-Table -AutoSize
Write-Host "대기열 체험은 예매 시작 직전에 02-seed-demo-queue.ps1을 실행하세요."
