# 도메인 및 데이터베이스 설계

## 1. 주요 도메인

| 도메인 | 설명 | 주요 속성 | 담당자 |
| --- | --- | --- | --- |
| User | 예매 사이트 회원과 권한 관리 | 이메일, 비밀번호, 이름, 닉네임, 휴대폰 번호, 권한, 상태 | A: 김재환 |
| Team | 야구 구단 정보 관리 | 팀명, 홈구장, 상태 | C: 조하린 |
| Stadium | 구장과 좌석 배치 관리 | 구장명, 주소, 좌석 구역, 물리 좌석 | C: 조하린 |
| Game | 경기 일정과 예매 상태 관리 | 홈팀, 원정팀, 구장, 경기 일시, 예매 오픈/마감 | C: 조하린 |
| SeatInventory | 경기별 좌석 재고와 선점 상태 관리 | 경기 좌석, 가격, 판매 상태, 선점 만료 시각 | C: 조하린 |
| Queue | 예매 대기열과 입장 토큰 관리 | 대기열 기록, 입장 토큰, 만료 시각 | B: 전윤현 |
| Reservation | 좌석 임시 선점 관리 | 예약 번호, 선점 좌석, 선점 만료 시각 | C: 조하린 |
| Order | 주문과 주문 항목 관리 | 주문 번호, 주문 좌석, 총 금액, 주문 상태 | B: 전윤현 |
| Payment | 결제 요청과 결과 관리 | 결제 번호, 결제 수단, 금액, 멱등키, 결제 상태 | D: 박현수 |
| PaymentRecoveryTask | 결제 승인·부분 취소 복구 작업 관리 | 복구 대상, 복구 유형(승인 확인/부분 취소), 복구 상태, 시도 횟수, 다음 재시도 시각 | D: 박현수 |
| PaymentCancel | 결제 부분·전체 취소 이력 관리 | 취소 대상 티켓, 취소 금액, PG 취소 키, 취소 상태 | D: 박현수 |
| Ticket | 결제 후 발급되는 티켓 관리 | 티켓 번호, 소유자, 경기 좌석, QR 토큰, 티켓 상태 | E: 유명인 |

## 2. 도메인 관계

- User와 주요 도메인의 관계:
    - User는 예약, 주문, 결제, 티켓, 대기열 기록을 생성하거나 소유한다.
- 주요 도메인 간 관계:
    - Team은 Stadium을 홈구장으로 참조한다.
    - Game은 홈팀, 원정팀, 구장을 참조한다.
    - Stadium은 SeatZone과 Seat를 가진다.
    - GameSeat는 특정 Game과 특정 Seat를 연결한 경기별 좌석 재고다.
    - Reservation은 여러 ReservationSeat를 가지고, ReservationSeat는 GameSeat를 참조한다.
    - Order는 Reservation에서 생성되고, 여러 OrderItem을 가진다.
    - OrderItem은 GameSeat와 연결되고, 결제 성공 후 Ticket 1장을 발급한다.
    - PaymentRecoveryTask는 Payment를 N:1로 참조하고, 부분 취소 복구(`PARTIAL_CANCEL`)인 경우 대상 PaymentCancel도 함께 참조한다. 승인 확인(`CONFIRM_UNKNOWN`)과 부분 취소 복구가 같은 결제에 동시에 여러 건 존재할 수 있어 1:1이 아닌 N:1 관계다.
    - PaymentCancel은 Payment를 N:1로 참조하고, 취소 대상 Ticket을 참조한다.
    - PaymentCancel는 하나의 결제에 대해 좌석 수만큼 취소 이력이 쌓일 수 있다.
- 소유권이 있는 데이터:
    - User가 소유하는 데이터: reservations, orders, payments, tickets
    - Ticket의 현재 소유자는 `tickets.user_id`로 판단한다.
- 참조만 하는 데이터:
    - teams, stadiums, seat_zones, seats는 기준 데이터다.
    - games는 경기 일정 기준 데이터이며, 예매 흐름에서 참조된다.
- 삭제 정책:
    - 회원, 경기, 좌석, 주문, 결제, 티켓은 물리 삭제하지 않고 status로 비활성화하거나 취소 처리한다.
    - 기준 데이터는 운영 중 참조될 수 있으므로 삭제 대신 INACTIVE 상태로 변경한다.
    - 대기열 기록과 토큰은 운영 정책에 따라 보관 기간 이후 배치로 정리할 수 있다.
    - 회원 탈퇴는 `tickets.status`가 `ISSUED`(환불 기한 내)·`REFUND_PENDING`·`REFUND_FAILED`인 티켓이 0건일 때만 가능하다.
    - 환불이 진행 중이거나 실패한 티켓은 정산이 확정되지 않았으므로 탈퇴를 차단한다.
- 상태 변경 규칙:
    - 좌석 재고는 `AVAILABLE -> HELD -> SOLD` 또는 `AVAILABLE -> HELD -> AVAILABLE` 흐름을 가진다.
    - 예약은 좌석 선점 시 HOLDING, 결제 성공 시 CONFIRMED, 만료 시 EXPIRED, 취소 시 CANCELED가 된다.
    - 주문은 생성 후 결제 성공 시 PAID, 취소/만료 시 CANCELED 또는 EXPIRED가 된다.
    - 티켓은 발급 후 ISSUED가 되고, 환불 요청 시 REFUND_PENDING을 거쳐 REFUNDED 또는 REFUND_FAILED가 된다. 입장 검표 성공 시 USED_ENTERED, 미입장 상태로 경기가 종료되면 USED_NO_SHOW가 된다.
    - 좌석 재고는 티켓이 REFUNDED로 확정된 시점에만 AVAILABLE로 되돌린다.

## 3. 테이블 목록

### 3.1 회원 (users)

예매 사이트 회원 정보를 저장한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 사용자 ID |
| email | VARCHAR(255) | UK, NOT NULL | 이메일(아이디) |
| password | VARCHAR(255) | NULL | 비밀번호 |
| name | VARCHAR(50) | NOT NULL | 이름(실명) |
| nickname | VARCHAR(50) | NULL | 닉네임 |
| phone | VARCHAR(20) | UK, NULL | 전화번호 |
| ci | VARCHAR(255) | UK, NULL | 본인 인증 연계 정보 |
| is_verified | BOOLEAN | NOT NULL, DEFAULT FALSE | 본인 인증 완료 여부 |
| provider | VARCHAR(50) | NULL | 소셜 로그인 제공자 |
| provider_id | VARCHAR(255) | NULL | 소셜 로그인 제공자의 사용자 식별자 |
| role | VARCHAR(20) | NOT NULL, DEFAULT ‘USER’ | 권한 |
| status | VARCHAR(20) | NOT NULL, DEFAULT ‘ACTIVE’ | 회원 상태 |
| created_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 생성일 |
| updated_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 수정일 |
- 제약 및 인덱스

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| uk_users_email | email | 이메일 중복 방지 |
| uk_users_phone | phone | 전화번호 중복 방지 |
| uk_users_ci | ci | 본인 인증 연계 정보 중복 방지 |
| uk_users_provider_provider_id | provider, provider_id | 소셜 로그인 계정 중복 연결 방지 |
- 상태값

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| role | USER | 일반 사용자 | 회원가입 시 기본값 |
| role | ADMIN | 관리자 | 운영자가 권한 부여 |
| status | ACTIVE | 정상 회원 | 가입 성공 |
| status | SUSPENDED | 이용 정지 | 운영자 제재 |
| status | DELETED | 탈퇴 처리 | 회원 탈퇴 또는 관리자 삭제 |
- Refresh Token  관리 테이블

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 토큰 식별 고유 ID |
| user_id | BIGINT | FK (users.id), NOT NULL, UNIQUE | 토큰의 주인 (회원 테이블 외래키) |
| token_value | VARCHAR(255) | NOT NULL, UNIQUE | 실제 암호화된 Refresh Token 문자열 |
| expired_at | DATETIME(6) | NOT NULL | 토큰 만료 일시 (유효성 검증 및 배치 삭제용) |
- Refresh Token 제약 및 인덱스

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_refresh_token_user | user_id | refresh_token N:1 users, Refresh Token은 하나의 사용자를 참조 |
| uk_refresh_token_user | user_id | 사용자당 Refresh Token 하나만 허용 |

### 3.2 구장 (stadiums)

야구 경기가 열리는 구장 정보를 저장한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 구장 ID |
| name | VARCHAR(100) | UK, NOT NULL | 구장명 |
| address | VARCHAR(255) | NOT NULL | 주소 |
| total_capacity | INT | NOT NULL | 총 좌석 수 |
| status | VARCHAR(20) | NOT NULL, DEFAULT ‘ACTIVE’ | 구장 상태 |
| created_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 생성일 |
| updated_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 수정일 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| uk_stadiums_name | name | 구장명 중복 방지 |
- **상태값**

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| status | ACTIVE | 사용 중인 구장 | 기본값 |
| status | INACTIVE | 사용하지 않는 구장 | 운영자가 비활성화 |

### 3.3 구단 (teams)

야구 구단 정보를 저장한다. 각 구단은 홈구장을 참조한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 팀 ID |
| name | VARCHAR(100) | UK, NOT NULL | 팀명 |
| home_stadium_id | BIGINT | FK, NOT NULL | 홈구장 ID |
| status | VARCHAR(20) | NOT NULL, DEFAULT ‘ACTIVE’ | 팀 상태 |
| created_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 생성일 |
| updated_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 수정일 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_teams_home_stadium | home_stadium_id | teams N:1 stadiums, 팀은 하나의 홈구장을 참조 |
| uk_teams_name | name | 팀명 중복 방지 |
| idx_teams_home_stadium | home_stadium_id | 홈구장 기준 팀 조회 |
- **상태값**

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| status | ACTIVE | 사용 중인 팀 | 기본값 |
| status | INACTIVE | 비활성화된 팀 | 운영자가 비활성화 |

### 3.4 좌석 구역 (seat_zones)

구장 안의 좌석 구역을 저장한다. 가격 등급과 구역별 좌석 조회에 사용한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 좌석 구역 ID |
| stadium_id | BIGINT | FK, NOT NULL | 구장 ID |
| name | VARCHAR(100) | NOT NULL | 구역명 |
| grade | VARCHAR(20) | NOT NULL | 좌석 등급 |
| base_price | INT | NOT NULL | 기본 가격 |
| created_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 생성일 |
| updated_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 수정일 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_seat_zones_stadium | stadium_id | seat_zones N:1 stadiums, 구장은 여러 좌석 구역을 가짐 |
| uk_seat_zones_stadium_name | stadium_id, name | 같은 구장 안에서 구역명 중복 방지 |
| idx_seat_zones_stadium | stadium_id | 구장별 좌석 구역 조회 |
- **상태값**

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| grade | INFIELD | 내야 |  |
| grade | OUTFIELD | 외야 |  |

### 3.5 좌석 (seats)

구장에 실제로 존재하는 좌석을 저장한다. 좌석도 표시를 위해 좌표 컬럼을 둔다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 좌석 ID |
| stadium_id | BIGINT | FK, NOT NULL | 구장 ID |
| zone_id | BIGINT | FK, NOT NULL | 좌석 구역 ID |
| seat_block | VARCHAR(20) | NOT NULL | 블록 |
| seat_row | VARCHAR(20) | NOT NULL | 열 |
| seat_number | VARCHAR(20) | NOT NULL | 좌석 번호 |
| x_position | INT | NULL | 좌석도 X 좌표 |
| y_position | INT | NULL | 좌석도 Y 좌표 |
| status | VARCHAR(20) | NOT NULL, DEFAULT ‘ACTIVE’ | 좌석 상태 |
| created_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 생성일 |
| updated_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 수정일 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_seats_stadium | stadium_id | seats N:1 stadiums, 구장은 여러 물리 좌석을 가짐 |
| fk_seats_zone | zone_id | seats N:1 seat_zones, 좌석 구역은 여러 물리 좌석을 가짐 |
| uk_seats_location | stadium_id, zone_id, seat_block, seat_row, seat_number | 같은 구장/구역 안에서 좌석 중복 방지 |
| idx_seats_zone_status | zone_id, status | 구역별 활성 좌석 조회 |
- **상태값**

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| status | ACTIVE | 예매에 사용할 수 있는 좌석 | 기본값 |
| status | INACTIVE | 사용하지 않는 좌석 | 운영자가 비활성화 |

### 3.6 경기 (games)

경기 일정과 예매 상태를 저장한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 경기 ID |
| home_team_id | BIGINT | FK, NOT NULL | 홈 팀 ID |
| away_team_id | BIGINT | FK, NOT NULL | 원정 팀 ID |
| stadium_id | BIGINT | FK, NOT NULL | 구장 ID |
| game_at | DATETIME | NOT NULL | 경기 일시 |
| booking_open_at | DATETIME | NOT NULL | 예매 오픈 일시 |
| booking_close_at | DATETIME | NOT NULL | 예매 마감 일시 |
| booking_status | VARCHAR(20) | NOT NULL, DEFAULT ‘SCHEDULED’ | 예매 상태 |
| title | VARCHAR(255) | NULL | 화면 표시용 경기명 |
| created_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 생성일 |
| updated_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 수정일 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_games_home_team | home_team_id | games N:1 teams, 팀은 여러 홈 경기를 가질 수 있음 |
| fk_games_away_team | away_team_id | games N:1 teams, 팀은 여러 원정 경기를 가질 수 있음 |
| fk_games_stadium | stadium_id | games N:1 stadiums, 구장은 여러 경기를 개최할 수 있음 |
| uk_games_stadium_game_at | stadium_id, game_at | 같은 구장·같은 일시 중복 경기 등록 방지 |
| idx_games_game_at | game_at | 날짜별 경기 조회 |
| idx_games_home_team_game_at | home_team_id, game_at | 홈 팀별 경기 조회 |
| idx_games_away_team_game_at | away_team_id, game_at | 원정 팀별 경기 조회 |
| idx_games_stadium_game_at | stadium_id, game_at | 구장별 경기 조회 (중복 방지용 UNIQUE와 목적이 달라 병존) |
- **상태값**

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| booking_status | SCHEDULED | 예매 오픈 전 | 경기 등록 시 기본값 |
| booking_status | OPEN | 예매 가능 | 예매 오픈 시각 도달 또는 운영자 오픈 |
| booking_status | CLOSED | 예매 마감 | 예매 마감 시각 도달 또는 운영자 마감 |
| booking_status | CANCELLED | 경기 취소 | 운영자가 경기 취소 |

### 3.7 경기 좌석 재고 (game_seats)

특정 경기에서 판매되는 좌석 재고를 저장한다. 실제 예매와 동시성 제어는 이 테이블을 기준으로 처리한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 경기 좌석 ID |
| game_id | BIGINT | FK, NOT NULL | 경기 ID |
| seat_id | BIGINT | FK, NOT NULL | 좌석 ID |
| price | INT | NOT NULL | 경기별 판매 가격 |
| status | VARCHAR(20) | NOT NULL, DEFAULT ‘AVAILABLE’ | 경기 좌석 판매 상태 |
| version | BIGINT | NOT NULL, DEFAULT 0 | 낙관적 락용 버전 |
| hold_expires_at | DATETIME | NULL | 선점 만료 시각 |
| sold_at | DATETIME | NULL | 판매 완료 시각 |
| created_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 생성일 |
| updated_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 수정일 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_game_seats_game | game_id | game_seats N:1 games, 경기는 여러 경기 좌석 재고를 가짐 |
| fk_game_seats_seat | seat_id | game_seats N:1 seats, 물리 좌석은 경기마다 여러 재고로 생성될 수 있음 |
| uk_game_seats_game_seat | game_id, seat_id | 같은 경기에서 같은 좌석 재고 중복 방지 |
| idx_game_seats_game_status_expires | game_id, status, hold_expires_at | 경기별 좌석 상태 조회 및 만료 선점 좌석 판별 |
| idx_game_seats_hold_expires | status, hold_expires_at | 만료된 선점 좌석 정리 |
- **상태값**

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| status | AVAILABLE | 예매 가능 | 경기 좌석 생성 또는 선점 만료 |
| status | HELD | 임시 선점 | 사용자가 좌석 선점 성공 |
| status | SOLD | 판매 완료 | 결제 승인 완료 |
| status | BLOCKED | 관리자 차단 | 운영자가 판매 차단 |

### 3.8 대기열 이력 (queue_entry_histories)

예매 대기열 진입 기록을 저장한다. 실제 순번 처리는 Redis에서 담당하고 DB에는 기록과 상태를 남긴다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 대기열 이력 ID |
| game_id | BIGINT | FK, NOT NULL | 경기 ID |
| user_id | BIGINT | FK, NOT NULL | 사용자 ID |
| queue_key | VARCHAR(100) | NOT NULL | Redis 대기열 키 |
| status | VARCHAR(20) | NOT NULL, DEFAULT ‘WAITING’ | 대기열 상태 |
| entered_at | DATETIME(6) | NOT NULL | 대기열 진입 시각 |
| admitted_at | DATETIME(6) | NULL | 입장 허용 시각 |
| canceled_at | DATETIME(6) | NULL | 대기 취소 시각 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_queue_entry_histories_game | game_id | queue_entry_histories N:1 games, 경기는 여러 대기열 이력을 가짐 |
| fk_queue_entry_histories_user | user_id | queue_entry_histories N:1 users, 사용자는 여러 대기열 이력을 가질 수 있음 |
| uk_queue_entry_histories_queue_key | queue_key | Redis 대기열 키 중복 방지 |
| idx_queue_entry_histories_game_user | game_id, user_id | 사용자 대기열 상태 조회 |
| idx_queue_entry_histories_game_status | game_id, status | 경기별 대기열 상태 조회 |
- **상태값**

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| status | WAITING | 대기 중 | 대기열 진입 |
| status | ADMITTED | 예매 화면 입장 허용 | 순번 도달 |
| status | CANCELED | 사용자 또는 시스템 취소 | 사용자 취소·SSE 재연결 유예 시간 만료 또는 운영자 취소 |

### 3.9 입장 토큰 (admission_tokens)

대기열을 통과한 사용자에게 발급되는 예매 화면 입장 토큰을 저장한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 토큰 ID |
| game_id | BIGINT | FK, NOT NULL | 경기 ID |
| user_id | BIGINT | FK, NOT NULL | 사용자 ID |
| token | VARCHAR(255) | UK, NOT NULL | 입장 토큰 |
| status | VARCHAR(20) | NOT NULL, DEFAULT ‘ACTIVE’ | 입장 토큰 상태 |
| issued_at | DATETIME(6) | NOT NULL | 발급 시각 |
| expires_at | DATETIME(6) | NOT NULL | 만료 시각 |
| seat_browsing_expires_at | DATETIME(6) | NOT NULL | 최초 좌석 탐색 만료 시간 |
| seat_browsing_completed_at | DATETIME(6) | NULL | 최초 좌석 선점 완료 시간
최초에 한 번만 기록하며 취소 · 재선점 시 유지 |
| used_at | DATETIME(6) | NULL | 사용 시각 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_admission_tokens_game | game_id | admission_tokens N:1 games, 경기는 여러 입장 토큰을 발급할 수 있음 |
| fk_admission_tokens_user | user_id | admission_tokens N:1 users, 사용자는 여러 입장 토큰을 받을 수 있음 |
| uk_admission_tokens_token | token | 입장 토큰 중복 방지 |
| idx_admission_tokens_game_user | game_id, user_id | 사용자별 경기 입장 토큰 조회 |
| idx_admission_tokens_status_expires | status, expires_at | 만료 토큰 정리 |
- **상태값**

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| status | ACTIVE | 사용 가능한 입장 토큰 | 대기열 통과 후 발급 |
| status | USED | 사용 완료된 입장 토큰 | 티켓 발급 성공 시 |
| status | EXPIRED | 만료된 입장 토큰 | expires_at 경과 |
| status | REVOKED | 사용이 취소된 입장 토큰 | 대기 취소 또는 SSE 재연결 유예 시간 만료 |
| status | BROWSING_EXPIRED | 최초 좌석 탐색 시간이 만료된 입장 토큰 | 최초 선점 없이 seat_browsing_expires_at 경과 |

### 3.10 예약 (reservations)

사용자의 좌석 임시 선점 묶음을 저장한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 예약 ID |
| reservation_no | VARCHAR(50) | UK, NOT NULL | 예약 번호 |
| user_id | BIGINT | FK, NOT NULL | 사용자 ID |
| game_id | BIGINT | FK, NOT NULL | 경기 ID |
| status | VARCHAR(20) | NOT NULL, DEFAULT ‘HOLDING’ | 예약 상태 |
| hold_expires_at | DATETIME | NOT NULL | 선점 만료 시각 |
| created_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 생성일 |
| updated_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 수정일 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_reservations_user | user_id | reservations N:1 users, 사용자는 여러 예약을 가질 수 있음 |
| fk_reservations_game | game_id | reservations N:1 games, 경기는 여러 예약을 가질 수 있음 |
| uk_reservations_no | reservation_no | 예약 번호 중복 방지 |
| idx_reservations_user | user_id | 사용자별 예약 조회 |
| idx_reservations_game_status | game_id, status | 경기별 예약 상태 조회 |
| idx_reservations_hold_expires | status, hold_expires_at | 만료 예약 정리 |
- **상태값**

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| status | HOLDING | 좌석 선점 중 | 좌석 선점 성공 |
| status | CONFIRMED | 결제 성공으로 확정 | 결제 승인 완료 |
| status | CANCELED | 사용자 또는 시스템 취소 | HOLDING 상태의 사용자 취소·결제 실패 또는
CONFIRMED 상태의 경기 전체 취소·관리자 취소 |
| status | EXPIRED | 선점 시간 만료 | hold_expires_at 경과 |

### 3.11 예약 좌석 (reservation_seats)

예약에 포함된 좌석 목록을 저장한다. 여러 좌석을 한 번에 예매할 수 있으므로 예약 묶음과 좌석 목록을 분리한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 예약 좌석 ID |
| reservation_id | BIGINT | FK, NOT NULL | 예약 ID |
| game_seat_id | BIGINT | FK, NOT NULL | 경기 좌석 ID |
| price | INT | NOT NULL | 선점 당시 가격 |
| created_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 생성일 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_reservation_seats_reservation | reservation_id | reservation_seats N:1 reservations, 예약은 여러 좌석을 가질 수 있음 |
| fk_reservation_seats_game_seat | game_seat_id | reservation_seats N:1 game_seats, 예약 좌석은 하나의 경기 좌석을 참조 |
| uk_reservation_seats_game_seat_reservation | game_seat_id, reservation_id | 동일 예약 내 같은 좌석의 중복 삽입 방지 |
| idx_reservation_seats_reservation | reservation_id | 예약별 좌석 목록 조회 |

> 기존 `uk_reservation_seats_game_seat`(game_seat_id 단일 컬럼, 전역 UNIQUE)는 취소·만료로 `AVAILABLE`이 된 좌석을 재선점하면 `DataIntegrityViolationException`(500)을 유발했다. 
Over-booking 방지는 `game_seats` 상태 머신 + 분산 락 + 재검증으로 이미 보장되므로, 전역 UNIQUE는 정상 재선점을 막는 부작용만 있었다. 동일 예약 내 같은 좌석 중복 삽입 방지라는 원래 목적은 `reservation_id`를 포함한 복합 UNIQUE로 대체했다.
> 

### 3.12 주문 (orders)

결제를 위한 주문 묶음을 저장한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 주문 ID |
| order_no | VARCHAR(50) | UK, NOT NULL | 주문 번호 |
| user_id | BIGINT | FK, NOT NULL | 사용자 ID |
| reservation_id | BIGINT | FK, UK, NOT NULL | 예약 ID |
| total_amount | INT | NOT NULL | 총 결제 금액 |
| payment_deadline | DATETIME | NOT NULL | 주문 결제 기한 |
| status | VARCHAR(20) | NOT NULL, DEFAULT ‘CREATED’ | 주문 상태 |
| created_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 생성일 |
| updated_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 수정일 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_orders_user | user_id | orders N:1 users, 사용자는 여러 주문을 가질 수 있음 |
| fk_orders_reservation | reservation_id | orders 1:1 reservations, 하나의 예약은 하나의 주문으로 전환 |
| uk_orders_no | order_no | 주문 번호 중복 방지 |
| uk_orders_reservation | reservation_id | 하나의 예약이 여러 주문으로 전환되는 것을 방지 |
| idx_orders_user_status | user_id, status | 내 주문 조회 |
- **상태값**

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| status | CREATED | 주문 생성 | 예약에서 주문 생성 |
| status | PAID | 결제 완료 | 결제 승인 완료 |
| status | CANCELED | 주문 취소 | 사용자 취소 또는 결제 실패 |
| status | EXPIRED | 결제 제한 시간 만료 | 결제 제한 시간 경과 |
| status | PARTIALLY_CANCELED | 일부 티켓 환불 완료 | 주문 항목 중 일부만 환불 확정 |

### 3.13 주문 항목 (order_items)

주문에 포함된 좌석 단위 결제 항목을 저장한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 주문 항목 ID |
| order_id | BIGINT | FK, NOT NULL | 주문 ID |
| game_seat_id | BIGINT | FK, NOT NULL | 경기 좌석 ID |
| price | INT | NOT NULL | 결제 가격 |
| created_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 생성일 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_order_items_order | order_id | order_items N:1 orders, 주문은 여러 주문 항목을 가질 수 있음 |
| fk_order_items_game_seat | game_seat_id | order_items N:1 game_seats, 주문 항목은 하나의 경기 좌석을 참조 |
| idx_order_items_order | order_id | 주문별 항목 조회 |

### 3.14 결제 (payments)

결제 요청과 결과를 저장한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 결제 ID |
| payment_no | VARCHAR(50) | NOT NULL, UK | 서비스 내부 결제 번호 |
| order_id | BIGINT | NOT NULL, FK, UK | 결제 대상 주문 ID |
| user_id | BIGINT | NOT NULL, FK | 결제를 요청한 사용자 ID |
| amount | INT | NOT NULL | 최초 결제 승인 금액 |
| method | VARCHAR(20) | NULL | PG 승인 응답으로 확인한 실제 결제 수단 |
| status | VARCHAR(20) | NOT NULL, DEFAULT ‘READY’ | 결제 처리 상태 |
| idempotency_key | VARCHAR(255) | NOT NULL, UK | 현재 유효한 결제 시도를 식별하는 멱등키 |
| pg_provider | VARCHAR(20) | NOT NULL, DEFAULT ‘MOCK’ | 결제를 처리한 PG 제공자 |
| pg_order_id | VARCHAR(100) | NOT NULL | PG에 전달한 주문 식별자 |
| pg_payment_key | VARCHAR(200) | NULL, UK | PG가 발급한 결제 식별자 |
| fail_reason | VARCHAR(200) | NULL | 결제 실패 사유 |
| queue_token | VARCHAR(255) | NULL | 승인 상태 불명확·승인 후 로컬 반영 실패 시, 복구 완료 단계에서 원본 Queue-Token을 최종화하기 위해 저장 |
| approved_at | TIMESTAMP | NULL | 결제 승인 시각 |
| failed_at | TIMESTAMP | NULL | 결제 실패 시각 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 수정 시각 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_payments_order | order_id | payments N:1 orders, 일반 예매 주문의 결제 시도를 저장 |
| fk_payments_user | user_id | payments N:1 users, 사용자는 여러 결제를 가질 수 있음 |
| uk_payments_no | payment_no | 결제 번호 중복 방지 |
| uk_payments_order | order_id | 주문당 결제 최대 한 건 유지 |
| uk_payments_idempotency_key | idempotency_key | 활성 결제 시도 키 중복 방지 |
| uk_payments_pg_payment_key | pg_payment_key | 동일 PG 결제의 중복 연결 방지 |
| idx_payments_order | order_id | 주문 기준 결제 조회 |
| idx_payments_user | user_id | 사용자 기준 결제 조회 |
| idx_payments_status | status | 결제 상태 기준 조회 |
| idx_payments_user_status | user_id, status | 사용자별 결제 상태 조회 |
- **상태값**

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| status | READY | 결제 요청이 생성되고 PG 승인 전인 상태 | 결제 요청 생성 시 |
| status | APPROVED | PG 승인이 완료된 상태 | 유효한 Toss 승인 응답을 확인한 경우 |
| status | FAILED | 승인 실패 또는 결제 기한 만료 상태 | READY 결제의 실패·만료가 확인된 경우 |
| status | PARTIALLY_CANCELED | 승인 금액 중 일부가 환불된 상태 | 취소 완료 후 잔여 금액이 0원보다 큰 경우 |
| status | CANCELED | 승인 금액 전체가 환불된 상태 | 취소 완료 후 잔여 금액이 0원인 경우 |
| pg_provider | MOCK | 모의 PG 사용 | 모의 결제 환경에서 처리한 경우 |
| pg_provider | TOSS | Toss Payments 사용 | Toss를 통해 결제를 처리한 경우 |
| pg_provider | KAKAO | 카카오 | - |
| pg_provider | NAVER | 네이버 | - |

> `canceled_amount`·`canceled_at` 컬럼은 두지 않는다. 취소 완료 금액은 저장하지 않고 `payment_cancels`에서 `status='DONE'`인 행을 조회해 산정한다(6.4 참고).
> 

### 3.15 결제 복구 작업 (payment_recovery_tasks)

결제 승인·취소 결과가 불명확하거나 로컬 상태 반영에 실패한 결제를 복구하기 위한 작업을 저장한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 복구 작업 ID |
| payment_id | BIGINT | NOT NULL, FK | 복구 대상 결제 ID |
| payment_cancel_id | BIGINT | NULL, FK | 부분 취소 복구 대상 이력 ID |
| type | VARCHAR(40) | NOT NULL | 복구 작업 유형 |
| recovery_key | VARCHAR(100) | NOT NULL, UK | 복구 유형과 대상 ID를 조합한 중복 방지 키 |
| status | VARCHAR(20) | NOT NULL, DEFAULT ‘PENDING’ | 복구 작업 처리 상태 |
| attempt_count | INT | NOT NULL, DEFAULT 0 | 실패한 자동 실행 횟수 |
| next_retry_at | TIMESTAMP | NULL | 다음 자동 재시도 예정 시각 |
| processing_started_at | TIMESTAMP | NULL | 현재 처리 시작 시각 |
| last_error | VARCHAR(500) | NULL | 최근 복구 실패 사유 |
| completed_at | TIMESTAMP | NULL | 복구 작업 완료 시각 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 복구 작업 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 복구 작업 수정 시각 |
- 제약 및 인덱스

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_payment_recovery_tasks_payment | payment_id | payment_recovery_tasks N:1 payments, 하나의 결제에 여러 복구 작업이 있을 수 있음 |
| fk_payment_recovery_tasks_payment_cancel | payment_cancel_id | payment_recovery_tasks N:1 payment_cancels, 부분 취소 복구 시에만 참조 |
| uk_payment_recovery_tasks_recovery_key | recovery_key | 동일 대상·복구 유형의 작업 중복 생성 방지 |
| idx_payment_recovery_tasks_status_retry | status, next_retry_at | 현재 실행 가능한 복구 작업 조회 |

> `payment_id` 단일 UNIQUE(`uk_payment_recovery_tasks_payment`)는 결제당 복구 작업을 1건으로 제한해, 부분 환불처럼 한 결제에 복구 대상이 여러 건(`PARTIAL_CANCEL`) 발생하는 경우를 표현하지 못해 제거했다. 이후 `(payment_id, type)` 복합 UNIQUE로 대체했으나, 같은 결제의 서로 다른 티켓 부분 취소가 여전히 구분되지 않아 `payment_cancel_id`까지 포함한 `recovery_key` 조합 UNIQUE로 최종 대체했다.
> 
- 상태값

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| type | CONFIRM_UNKNOWN | 승인 결과가 불명확한 결제 확인 및 필요 시 환불 | Toss 승인 요청 결과를 확인하지 못한 경우 |
| type | PARTIAL_CANCEL | 티켓 단위 PG 부분 취소 처리 | 티켓 부분 취소 이력이 접수된 경우 |
| status | PENDING | 최초 실행 대기 상태 | 복구 작업 생성 또는 FAILED 작업 재활성화 시 |
| status | PROCESSING | 처리기가 작업을 실행 중인 상태 | PENDING 또는 실행 시각이 지난 RETRY 작업을 선점한 경우 |
| status | RETRY | 일시적 실패 후 재시도를 기다리는 상태 | PG 5xx·타임아웃·불명확 응답 등이 발생한 경우 |
| status | COMPLETED | 복구 작업이 정상 완료된 상태 | 처리기가 성공 결과를 반환한 경우 |
| status | FAILED | 자동 복구가 종결된 상태 | 명시적 실패 또는 최대 재시도 횟수 도달 시 |

### 3.16 티켓 (tickets)

결제 성공 후 좌석별로 발급되는 티켓을 저장한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 티켓 ID |
| ticket_no | VARCHAR(50) | UK, NOT NULL | 티켓 번호 |
| user_id | BIGINT | FK, NOT NULL | 현재 소유자 ID |
| order_item_id | BIGINT | FK, UK, NOT NULL | 주문 항목 ID |
| game_id | BIGINT | FK, NOT NULL | 경기 ID |
| game_seat_id | BIGINT | FK, UK, NOT NULL | 경기 좌석 ID |
| status | VARCHAR(20) | NOT NULL, DEFAULT ‘ISSUED’ | 티켓 상태 |
| qr_token | VARCHAR(255) | UK, NOT NULL | QR 토큰 |
| cancel_reason | VARCHAR(30) | NULL | 티켓 취소 사유 유형 |
| cancel_detail | VARCHAR(255) | NULL | 티켓 취소 상세 사유 |
| issued_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 발급 시각 |
| used_at | DATETIME(6) | NULL | 사용 시각 |
| canceled_at | DATETIME(6) | NULL | 관리자 강제 취소 집행 시각 |
| refund_requested_at | DATETIME(6) | NULL | 환불 요청 접수 시각 |
| refunded_at | DATETIME(6) | NULL | 환불 확정 시각 |
| created_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 생성일 |
| updated_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 수정일 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_tickets_user | user_id | tickets N:1 users, 사용자는 여러 티켓을 소유할 수 있음 |
| fk_tickets_order_item | order_item_id | tickets 1:1 order_items, 주문 항목 하나에서 티켓 하나 발급 |
| fk_tickets_game | game_id | tickets N:1 games, 경기는 여러 티켓을 가질 수 있음 |
| fk_tickets_game_seat | game_seat_id | tickets 1:1 game_seats, 판매된 경기 좌석은 티켓 하나로 발급 |
| uk_tickets_no | ticket_no | 티켓 번호 중복 방지 |
| uk_tickets_order_item | order_item_id | 하나의 주문 항목에서 티켓이 중복 발급되는 것을 방지 |
| uk_tickets_game_seat | game_seat_id | 같은 경기 좌석의 티켓 중복 발급 방지 |
| uk_tickets_active_seat | active_seat_key | 좌석 점유 중인 티켓의 중복 발급 방지 |
| uk_tickets_qr_token | qr_token | QR 토큰 중복 방지 |
| idx_tickets_user_status | user_id, status | 내 티켓 조회 |
| idx_tickets_game | game_id | 경기별 티켓 조회 |

```sql
active_seat_key = IF(status = 'REFUNDED', NULL, game_seat_id)  -- generated column
```

> 기존 uk_tickets_game_seat(game_seat_id)를 그대로 두면 환불된 좌석을 다른 사용자가 재구매할 때 티켓 발급 단계에서 UNIQUE 위반이 발생한다(T3-03 차단). 
MySQL은 partial unique index를 지원하지 않으므로 generated column으로 우회한다. REFUND_PENDING·REFUND_FAILED는 좌석이 아직 SOLD이므로 NULL 대상에서 제외한다.
> 
- **상태값**

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| status | ISSUED | 발급 완료 | 결제 승인 후 티켓 발급 |
| status | USED | 사용 완료 | QR 검표 성공 |
| status | CANCELED | 취소 완료 | 사용자 환불 또는 관리자 강제 취소 |
| status | REFUND_PENDING | 환불 진행 중 | 취소 요청 접수, PG 취소 결과 대기 |
| status | REFUND_FAILED | 환불 실패 | PG 취소 실패, 재시도 또는 관리자 처리 대상 |
| status | REFUNDED | 환불 완료 | PG 취소 성공 및 결제 반영 완료 |
| status | USED_ENTERED | 입장 사용 완료 | QR 검표 성공 |
| status | USED_NO_SHOW | 미입장 자동 완료 | 경기 종료 후 배치 전이 |
| cancel_reason | USER_REFUND | 사용자 환불 요청 | - |
| cancel_reason | ADMIN_FORCE_CANCEL | 관리자 강제 취소 | - |
| cancel_reason | PAYMENT_CANCELED | 결제 취소에 따른 티켓 취소 | - |

### 3.17 결제 취소 이력 (payment_cancels)

결제의 부분·전체 취소 이력을 저장한다. PG 호출 전에 선기록해 중복 환불을 차단한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 결제 취소 이력 ID |
| payment_id | BIGINT | NOT NULL, FK | 취소 대상 결제 ID |
| ticket_id | BIGINT | NOT NULL, FK, UK | 취소 대상 티켓 ID |
| status | VARCHAR(20) | NOT NULL, DEFAULT ‘PENDING’ | PG 결제 취소 처리 상태 |
| reason | VARCHAR(200) | NOT NULL | PG에 전달하는 취소 사유 |
| pg_idempotency_key | VARCHAR(200) | NULL, UK | 현재 PG 취소 시도를 식별하는 멱등키 |
| pg_transaction_key | VARCHAR(200) | NULL | PG가 반환한 취소 거래 식별자 |
| failure_reason | VARCHAR(500) | NULL | PG 결제 취소 실패 사유 |
| completed_at | TIMESTAMP | NULL | PG 결제 취소 완료 시각 |
| failed_at | TIMESTAMP | NULL | PG 결제 취소 실패 시각 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 취소 이력 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 취소 이력 수정 시각 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_payment_cancels_payment | payment_id | payment_cancels N:1 payments, 결제는 여러 취소 이력을 가질 수 있음 |
| fk_payment_cancels_ticket | ticket_id | payment_cancels 1:1 tickets, 티켓당 취소 이력 1건 |
| uk_payment_cancels_ticket | ticket_id | 티켓당 취소 이력을 최대 한 건으로 제한 |
| uk_payment_cancels_pg_idempotency_key | pg_idempotency_key | 동일 PG 취소 시도의 중복 처리 방지 |
| idx_payment_cancels_payment_status | payment_id, status | 결제별 취소 상태 조회 |

> 실제 구현은 환불 금액을 별도 저장하지 않고 `ticket_id → order_items.price`로 조회 시점에 산정하는 방식이다. `reason` 한 컬럼으로, PG 키는 멱등키(`pg_idempotency_key`)와 거래 식별자(`pg_transaction_key`)로 분리했다.
> 
- **상태값**

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| status | PENDING | PG 취소 요청을 기다리거나 처리 중인 상태 | 신규 취소 접수 또는 FAILED 이력 재활성화 시 |
| status | DONE | PG 취소 완료가 확인된 상태 | Toss 취소 응답에서 금액·거래 키·완료 시각을 확인한 경우 |
| status | FAILED | PG가 취소 요청을 명시적으로 거절한 상태 | Toss 4xx 응답이 확인된 경우 |
- 재시도는 새 행을 만들지 않고 기존 `FAILED` 행을 `PENDING`으로 되돌려 새 `pg_idempotency_key`로 다시 시도한다. `uk_payment_cancels_ticket`이 티켓당 이력을 1건으로 유지해 이를 스키마 레벨에서 강제한다.

---

## 4. 추가 확장

### 4.1 접근 로그 (access_logs)

사용자 요청 기록을 저장한다. 이상 요청 탐지, 운영 통계, 보안 감사가 필요해질 때 추가한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 접근 로그 ID |
| user_id | BIGINT | FK, NULL | 사용자 ID |
| ip_address | VARCHAR(45) | NOT NULL | IP 주소 |
| user_agent | VARCHAR(500) | NULL | User-Agent |
| request_uri | VARCHAR(500) | NOT NULL | 요청 URI |
| http_method | VARCHAR(10) | NOT NULL | HTTP 메서드 |
| status_code | INT | NULL | 응답 상태 코드 |
| created_at | **TIMESTAMP(6)** | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 생성일 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_access_logs_user | user_id | access_logs N:1 users, 사용자 요청 로그를 저장 |
| idx_access_logs_user | user_id, created_at | 사용자별 접근 로그 조회 |
| idx_access_logs_ip | ip_address, created_at | IP별 접근 로그 조회 |
| idx_access_logs_created | created_at | 기간별 접근 로그 정리 |

### 4.2 의심 활동 (suspicious_activities)

매크로 의심, 과도한 요청, 비정상 예매 시도 같은 보안 이벤트를 저장한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 의심 활동 ID |
| user_id | BIGINT | FK, NULL | 사용자 ID |
| activity_type | VARCHAR(20) | NOT NULL | 의심 활동 유형 |
| reason | VARCHAR(255) | NOT NULL | 탐지 사유 |
| status | VARCHAR(20) | NOT NULL, DEFAULT ‘OPEN’ | 처리 상태 |
| detected_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 탐지 시각 |
| resolved_at | DATETIME(6) | NULL | 처리 완료 시각 |
| created_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) | 생성일 |
| updated_at | DATETIME(6) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 수정일 |
- **제약 및 인덱스**

| 이름 | 컬럼 | 설명 |
| --- | --- | --- |
| fk_suspicious_activities_user | user_id | suspicious_activities N:1 users, 사용자별 의심 활동을 저장 |
| idx_suspicious_activities_user | user_id, detected_at | 사용자별 의심 활동 조회 |
| idx_suspicious_activities_status | status, detected_at | 처리 상태별 의심 활동 조회 |
- **상태값**

| 컬럼 | 상태 | 의미 | 변경 가능 조건 |
| --- | --- | --- | --- |
| status | OPEN | 미처리 | 이상 활동 탐지 |
| status | RESOLVED | 처리 완료 | 운영자 확인 완료 |
| status | IGNORED | 오탐 처리 | 운영자가 오탐으로 판단 |

---

## 5. 공통 컬럼 규칙

- 기본키 타입: `BIGINT`
- 생성일 컬럼명: `created_at`
- 수정일 컬럼명: `updated_at`
- 삭제 방식: 주요 거래 데이터는 물리 삭제하지 않고 상태값으로 관리한다. 기준 데이터도 삭제 대신 `INACTIVE` 상태를 사용한다.
- 상태값 관리 방식: `VARCHAR(20)` 문자열로 관리하고, 상태값은 enum으로 제한한다.
- 시간대 기준:
    - 서비스·DB·API의 시간 기준을 `Asia/Seoul` 로 통일한다.
    - 모든 시간 컬럼은 `DATETIME(6)`을 사용한다.

---

## 6. 정합성 규칙

### 6.1 좌석(seats)

- `seats.stadium_id`는 `seats.zone_id`가 참조하는 `seat_zones.stadium_id`와 일치해야 한다.

### 6.2 결제(payments)

- `payments`는 `order_id`만 NOT NULL로 참조 한다.
- `payments.user_id`는 `orders.user_id`와 일치해야 한다.

### 6.3 티켓(tickets)

- `tickets.order_item_id`, `tickets.game_seat_id`, `tickets.game_id`는 같은 경기 좌석을 가리켜야 한다.

### 6.4 결제 취소(payment_cancels)

- `payments`는 `canceled_amount`를 별도 컬럼으로 저장하지 않는다. 취소 완료 금액은 `payment_cancels`에서 `status='DONE'`인 행을 `ticket_id → order_items.price`로 조인해 조회 시점에 산정한다.
- 산정한 취소 완료 금액 ≤ `payments.amount`
- 취소 완료 금액 = 0 → `payments.status = APPROVED`
- 0 < 취소 완료 금액 < `amount` → `payments.status = PARTIALLY_CANCELED`
- 취소 완료 금액 = `amount` → `payments.status = CANCELED`
- `payment_cancels.status`와 `tickets.status`는 다음 대응을 유지한다.
    - PENDING ↔ REFUND_PENDING / DONE ↔ REFUNDED / FAILED ↔ REFUND_FAILED
- `payment_cancels.ticket_id`가 속한 주문(`ticket → order_item → order`)은 `payment_cancels.payment_id`가 참조하는 결제의 `order_id`와 일치해야 한다.

> `payment_cancels.cancel_amount`가 `order_items.price`와 일치해야 한다는 규칙과 `order_item_id` 기준 정합성 규칙은 3.17 스키마 단순화로 해당 컬럼 자체가 없어져 더 이상 적용되지 않는다(9.2 결제 도메인 스키마 정리 참고).
> 

---

end.