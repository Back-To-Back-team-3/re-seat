# 9. 관리자 (admin) 페이지 API

> 기준 데이터(`teams`, `stadiums`, `seat_zones`, `seats`)는 마이그레이션 시드 또는 관리자 API로 등록한다. MVP에서는 시드 + 최소 관리자 API를 권장한다.
모든 관리자 상태 변경 API는 `admin_audit_logs`에 작업자·사유·변경 전후 값을 기록한다.
> 

### 9.1 구장 등록

- `POST /api/v1/admin/stadiums`
- 인증: JWT(ADMIN)
- 대응 테이블: `stadiums`
- 요청 바디:
    
    ```json
    {
        "name": "잠실",
        "address": "서울 송파구...",
        "totalCapacity": 25000
    }
    ```
    
- 응답(201):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "구장 등록 완료",
        "data": {
            "stadiumId": 5,
            "status": "ACTIVE"
        }
    }
    ```
    
- 에러: `INVALID_REQUEST`(400) / `FORBIDDEN`(403)

### 9.2 구단 등록

- `POST /api/v1/admin/teams`
- 인증: JWT(ADMIN)
- 대응 테이블: `teams`(home_stadium_id FK)
- 요청 바디:
    
    ```json
    {
        "name": "LG",
        "homeStadiumId": 5
    }
    ```
    
- 응답(201):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "구단 등록 완료",
        "data": {
            "teamId": 1,
            "status": "ACTIVE"
        }
    }
    ```
    
- 에러: `INVALID_REQUEST`(400) / `FORBIDDEN`(403) / `STADIUM_NOT_FOUND`(404)

### 9.3 좌석 구역/좌석 등록

- `POST /api/v1/admin/stadiums/{stadiumId}/zones`, `.../seats`
- 인증: JWT(ADMIN)
- 대응 테이블: `seat_zones`, `seats`
- 설명: 구역(grade, base_price)과 물리 좌석(block/row/number, x/y) 일괄 등록.
- 에러: `INVALID_REQUEST`(400) / `FORBIDDEN`(403) / `STADIUM_NOT_FOUND`(404)

### 9.4 경기 등록

- `POST /api/v1/admin/games`
- 인증: JWT(ADMIN)
- 대응 테이블: `games`
- 요청 바디
    
    ```json
    {
        "homeTeamId": 1,
        "awayTeamId": 2,
        "stadiumId": 5,
        "gameAt": "2026-07-11 18:30:00",
        "bookingOpenAt": "2026-07-04 14:00:00",
        "bookingCloseAt": "2026-07-11 18:30:00",
        "title": "LG vs 한화"
    }
    ```
    
- 응답(201):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "경기 등록 완료",
        "data": {
            "gameId": 10,
            "bookingStatus": "SCHEDULED"
        }
    }
    ```
    
- 에러:
    - `INVALID_REQUEST`(400) / 필수값 누락, 예매 오픈 시각이 마감 시각보다 늦거나 마감 시각이 경기 일시보다 늦은 경우
    - `SAME_TEAM_MATCH`(400, `homeTeamId == awayTeamId`)
    - `FORBIDDEN`(403)
    - `TEAM_NOT_FOUND`(404) / `STADIUM_NOT_FOUND`(404)
    - `DUPLICATE_GAME`(409, 동일 `stadiumId`·`gameAt` 조합으로 이미 등록된 경기가 있음)
- 동시성/멱등 보장: `existsBy` 사전 검증(순차 요청 방어) + `games(stadium_id, game_at)` UNIQUE 제약(완전 동시 요청 최종 방어선). 두 방어선 모두 `DUPLICATE_GAME`으로 응답을 통일한다.
- 프론트 계약: 좌석 재고 생성은 이 API 책임이 아니다(§9.5 좌석 재고 오픈 API 별도 호출 필요). 관리자 화면에서 "경기 등록"과 "좌석 재고 오픈"을 별개 단계로 노출한다.

### 9.5 경기 좌석 재고 오픈

- `POST /api/v1/admin/games/{gameId}/seats`
- 인증: JWT(ADMIN)
- 대응 테이블: `game_seats`(생성, status=AVAILABLE, version=0)
- 설명: 해당 경기의 판매 좌석 재고를 생성한다.
    - 구역별 가격을 지정하면 `seats`를 기반으로 `game_seats`를 일괄 생성한다.
    - `uk_game_seats_game_seat`로 중복 방지
    - 동일 경기에 대한 재고 오픈은 원자적으로 1회만 성공한다.
- 요청 바디:
    
    ```json
    {
        "zonePrices": [
            {
                "zoneId": 30,
                "price": 18000
            }
        ]
    }
    ```
    
- 응답(201):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "좌석 재고 오픈 성공",
        "data": {
            "gameId": 10,
            "createdSeatCount": 25000
        }
    }
    ```
    
- 에러:
    - `FORBIDDEN`(403)
    - `GAME_NOT_FOUND`(404) / `ZONE_NOT_FOUND`(404)
    - `SEAT_INVENTORY_ALREADY_OPENED`(409, 재호출)

### 9.6 좌석 판매 차단

- `POST /api/v1/admin/game-seats/{gameSeatId}/block`
- 인증: JWT(ADMIN)
- 대응 테이블: `game_seats`(status→BLOCKED)
- 설명:
    - `AVAILABLE` 좌석만 `BLOCKED`로 전이할 수 있다.
    - `HELD`·`SOLD` 좌석은 차단 대상에서 제외하고 사유를 응답에 명시한다.
- 요청 바디:
    
    ```json
    {        
    		"reason": "매크로 의심 좌석 임시 차단"    
    }
    ```
    
    - `reason`은 필수이며 최대 255자, 개행 문자(CR/LF)를 포함할 수 없다.
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "좌석 판매 차단 완료",
        "data": {
            "gameSeatId": 5001,
            "status": "BLOCKED"
        }
    }
    ```
    
- 에러:
    - `INVALID_REQUEST`(400, `reason` 누락)
    - `FORBIDDEN`(403)
    - `GAME_SEAT_NOT_FOUND`(404)
    - `INVALID_STATE_TRANSITION`(409, `AVAILABLE`이 아닌 좌석)
- 동시성/멱등 보장:
    - `GameSeat`의 낙관적 락(`version`)으로 동시 요청 경합을 방어한다.
    - 사유(`reason`)는 `admin_audit_logs` 인프라 부재로 별도 테이블에 저장하지 않고, 상태 전이 커밋이 성공한 이후에만 애플리케이션 로그로 기록한다(커밋 실패·롤백 시 로그 미기록).

### 9.7 좌석 판매 차단 해제

- `POST /api/v1/admin/game-seats/{gameSeatId}/unblock`
- 인증: JWT(ADMIN)
- 대응 테이블: `game_seats`(status: BLOCKED → AVAILABLE)
- 설명: `BLOCKED` 좌석만 `AVAILABLE`로 되돌린다.
- 요청 바디:
    
    ```json
    {        
    		"reason": "차단 사유 해소"    
    }
    ```
    
    - `reason` 검증 규칙은 9.6과 동일하다(필수, 최대 255자, 개행 문자 불가).
- 응답(200):
    
    ```json
        {
            "success": true,
            "errorCode": null,
            "message": "좌석 차단 해제 완료",
            "data": {
                "gameSeatId": 5001,
                "status": "AVAILABLE"
            }
        }
    ```
    
- 에러:
    - `INVALID_REQUEST`(400, `reason` 누락·255자 초과·개행 문자 포함) /
    - `FORBIDDEN`(403) /
    - `GAME_SEAT_NOT_FOUND`(404) /
    - `INVALID_STATE_TRANSITION`(409, `BLOCKED`가 아닌 좌석)
- 동시성/멱등 보장: 9.6과 동일하게 낙관적 락으로 방어하며, 로그도 커밋 성공 이후에만 기록한다.

### 9.8 의심 활동 조회(확장)

- `GET /api/v1/admin/suspicious-activities`
- 인증: JWT(ADMIN)
- 대응 테이블: `suspicious_activities`(+ `access_logs`)
- 요청 파라미터: `status`(OPEN/RESOLVED/IGNORED), `userId`, `page`, `size`
- 응답(200): 매크로/과도 요청/비정상 예매 의심 이벤트 목록
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "의심 활동 조회",
        "data": {
            "content": [
                {
                    "activityId": 1,
                    "userId": 1,
                    "type": "EXCESSIVE_REQUEST",
                    "status": "OPEN",
                    "detectedAt": "2026-07-13 21:00:00"
                }
            ],
            "pageNumber": 0,
            "pageSize": 20,
            "totalElements": 1,
            "totalPages": 1,
            "isFirst": true,
            "isLast": true
        }
    }
    ```
    

### 9.9 회원 목록 조회

- `GET /api/v1/admin/users`
- 인증: JWT (ADMIN)
- 설명: 전체 회원 목록을 검색 조건과 페이징을 적용하여 조회한다.
- 요청 파라미터: `email`, `name`, `nickname`, `phone`, `role`, `status`, `page`, `size`, `sort`
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "회원 목록 조회 완료",
        "data": {
            "content": [
                {
                    "id": 1,
                    "email": "user@example.com",
                    "name": "홍길동",
                    "nickname": "길동이",
                    "phone": "010-1234-5678",
                    "role": "USER",
                    "status": "ACTIVE",
                    "isVerified": false,
                    "createdAt": "2026-07-13 21:00:00",
                    "updatedAt": "2026-07-13 21:00:00"
                }
            ],
            "pageNumber": 0,
            "pageSize": 20,
            "totalElements": 1,
            "totalPages": 1,
            "isFirst": true,
            "isLast": true
        }
    }
    ```
    

### 9.10 회원 상세 조회

- `GET /api/v1/admin/users/{userId}`
- 인증: JWT(ADMIN)
- 설명: 특정 회원의 상세 정보를 조회합니다.
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "회원 상세 조회 완료",
        "data": {
            "id": 1,
            "email": "user@example.com",
            "name": "홍길동",
            "nickname": "길동이",
            "phone": "010-1234-5678",
            "role": "USER",
            "status": "ACTIVE",
            "isVerified": false,
            "createdAt": "2026-07-13 21:00:00",
            "updatedAt": "2026-07-13 21:00:00"
        }
    }
    ```
    
- 에러: `INVALID_REQUEST`(400) / `FORBIDDEN`(403) / `USER_NOT_FOUND`(404)

### 9.11 회원 권한 변경

- `PATCH /api/v1/admin/users/{userId}/role`
- 인증: JWT(ADMIN)
- 대응 테이블: `users`(role)
- 설명: 특정 회원의 시스템 권한(USER, ADMIN  등)을 변경한다.
- 요청 바디:

```json
{
    "role": "ADMIN"
}
```

- 응답(200):

```json
{
    "success": true,
    "errorCode": null,
    "message": "회원 권한 변경 완료",
    "data": null
}
```

- 에러: `INVALID_REQUEST`(400) / `FORBIDDEN`(403) / `USER_NOT_FOUND`(404)

### 9.12 회원 상태 변경

- `PATCH /api/v1/admin/users/{userId}/status`
- 인증: JWT(ADMIN)
- 대응 테이블: `users`(status)
- 설명: 특정 회원의 상태(ACTIVE, INACTIVE, DELETED 등)를 변경한다.
- 요청 바디:

```json
{
    "status": "INACTIVE",
    "reason": "매크로 이용 의심"
}
```

- 응답(200):

```json
{
    "success": true,
    "errorCode": null,
    "message": "회원 상태 변경 완료",
    "data": null
}
```

- 에러: `INVALID_REQUEST`(400) / `FORBIDDEN`(403) / `USER_NOT_FOUND`(404)

### 9.13 예매 오픈/마감 상태 전이

- `PATCH /api/v1/admin/games/{gameId}/booking-status`
- 인증: JWT(ADMIN)
- 대응 테이블: `games`(booking_status)
- 설명: 경기의 예매 상태를 전이한다. `OPEN` 상태에서만 대기열 진입·좌석 선점이 가능하다.
    - 허용 전이: `SCHEDULED→OPEN`, `OPEN→CLOSED`, `SCHEDULED/OPEN→CANCELLED`
    - 경기별 예매 오픈은 원자적으로 1회만 성공한다. 관리자 두 명이 동시에 오픈해도 중복 전이가 발생하지 않는다(조건부 UPDATE 결과가 0건이면 경합 실패로 처리).
- 요청 바디:
    
    ```json
    {
        "bookingStatus": "OPEN",
        "reason": "예매 오픈 시각 도달"
    }
    ```
    
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "예매 상태 변경",
        "data": {
            "gameId": 10,
            "bookingStatus": "OPEN"
        }
    }
    ```
    
- 에러:
    - `INVALID_REQUEST`(400) /
        - `bookingStatus`가 `OPEN`/`CLOSED`/`CANCELLED` 중 하나가 아니거나 `reason` 누락
    - `FORBIDDEN`(403)
    - `GAME_NOT_FOUND`(404)
    - `INVALID_BOOKING_STATUS_TRANSITION`(409) / 허용되지 않은 전이 또는 동시 전이 경합
    - `SEAT_INVENTORY_NOT_OPENED`(409) / 좌석 재고 미오픈 상태에서 OPEN 시도

### 9.14 관리자 티켓 강제 취소

- `POST /api/v1/admin/tickets/{ticketId}/cancel`
- 인증: JWT(ADMIN)
- 대응 테이블: `tickets`, `payment_cancels`, `payments`, `game_seats`, `orders`
- 설명: 운영 사유로 티켓을 강제 취소한다.
    - 취소 로직은 8.3(사용자 티켓 취소)과 완전히 동일한 파이프라인을 공유한다.
    - `TicketService.cancelTicketByAdmin()`이 `ticket.requestRefund(ADMIN_FORCE_CANCEL, reason)`으로 `REFUND_PENDING`까지만 동기 처리하고, `PaymentService.requestTicketPaymentCancel()`을 호출한 뒤 즉시 반환한다.
    - 최종 `REFUNDED`/`CANCELED` 확정은 7.5의 `PartialCancelRecoveryHandler`(결제 복구 스케줄러)가 비동기로 수행한다.
- 요청 바디:
    
    ```json
    {
        "reason": "경기 우천 취소로 인한 전액 환불"
    }
    ```
    
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "관리자 직권 티켓 강제 취소 및 자원 반환 완료",
        "data": {
            "ticketId": 9051,
            "ticketNo": "TKT-20260711-000002",
            "status": "REFUND_PENDING",
            "cancelReason": "ADMIN_FORCE_CANCEL",
            "cancelDetail": "경기 우천 취소로 인한 전액 환불",
            "canceledAt": null,
            "gameSeatId": 5002,
            "seatStatus": "SOLD"
        }
    }
    ```
    
- 에러: `INVALID_REQUEST`(400, reason 누락) / `FORBIDDEN`(403) / `TICKET_NOT_FOUND`(404)

### 9.15 회원별 티켓 조회

- `GET /api/v1/admin/tickets/users/{userId}`
- 인증: JWT(ADMIN)
- 설명: 특정 회원이 보유한 티켓 목록을 조회한다.
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "사용자 티켓 소유 목록 조회 완료",
        "data": {
            "content": [
                {
                    "ticketId": 9050,
                    "ticketNo": "TKT-20260711-000001",
                    "status": "ISSUED",
                    "qrToken": "qr_...",
                    "issuedAt": "2026-07-11 14:29:05",
                    "usedAt": null,
                    "canceledAt": null,
                    "gameId": 10,
                    "gameTitle": "LG vs 한화",
                    "stadiumName": "잠실야구장",
                    "homeTeamName": "LG",
                    "awayTeamName": "한화",
                    "gameAt": "2026-07-11 18:30:00",
                    "seat": "1루 블루석 A-3-12",
                    "gameSeatId": 5001,
                    "zoneName": "1루 블루석",
                    "seatBlock": "A",
                    "seatRow": "3",
                    "seatNumber": "12"
                }
            ],
            "pageNumber": 0,
            "pageSize": 20,
            "totalElements": 1,
            "totalPages": 1,
            "isFirst": true,
            "isLast": true
        }
    }
    ```
    

### 9.16 환불 실패 티켓 조회

- `GET /api/v1/admin/tickets/refund-failed`
- 인증: JWT(ADMIN)
- 대응 테이블: `tickets`, `payment_cancels`
- 설명: `REFUND_FAILED` 상태 티켓 목록을 조회한다. 수동 재시도(#8.4) 또는 강제 취소(#9.13) 대상이다.
- 요청 파라미터: `gameId`(option), `page`, `size`
- 응답(200):

```json
{
    "success": true,
    "message": "환불 실패 티켓 조회",
    "data": {
        "content": [
            {
                "ticketId": 9051,
                "ticketNo": "TKT-20260711-000002",
                "userId": 1,
                "paymentCancelId": 8801,
                "cancelAmount": 18000,
                "attemptCount": 2,
                "failReason": "PG timeout",
                "requestedAt": "2026-07-10 15:00:00"
            }
        ],
        "pageNumber": 0,
        "pageSize": 20,
        "totalElements": 1,
        "totalPages": 1,
        "isFirst": true,
        "isLast": true
    }
}
```

### 9.17 QR 검표(입장 처리)

- Method / URI: `POST /api/v1/admin/tickets/verify`
- 인증: JWT + ADMIN
- 대응 테이블: `tickets`
- 설명: 관리자가 입장 게이트에서 QR 토큰과 경기 ID로 티켓을 조회해 입장 처리한다. `ISSUED` 상태의 티켓만 가능하며 성공 시 `USED_ENTERED`로 전이한다.
- 요청 바디: `{ qrToken: string, gameId: number }`
- 응답(200): `{ ticketId, status, usedAt, seat, holderName }`
- 에러:
    - `TICKET_NOT_FOUND`(404) / qrToken+gameId 조합의 티켓 없음
    - `TICKET_REFUND_IN_PROGRESS`(409) / `REFUND_PENDING` 상태
    - `TICKET_REFUND_FAILED_RETRY_REQUIRED`(409) / `REFUND_FAILED` 상태
    - `TICKET_ALREADY_REFUNDED`(409) / `REFUNDED` 상태
    - `TICKET_ALREADY_USED`(409) / 이미 `USED_ENTERED`·`USED_NO_SHOW`(재검표)
- 프론트 계약: 검표 단말에서 QR 스캔 직후 즉시 호출한다. 재스캔 시 `TICKET_ALREADY_USED`로 "이미 입장 처리됨" 안내와 구분한다.
- 동시성/멱등 보장: 별도 락 없이 `markEntered()`의 `ISSUED` 상태 가드로만 방어한다. 같은 티켓에 대한 동시 중복 스캔은 극히 드물게 경합할 수 있어 필요 시 후속 개선 여지가 있다.

### 9.18 QR 토큰 재발급

- Method / URI: `POST /api/v1/admin/tickets/{ticketId}/qr/reissue`
- 인증: JWT + ADMIN
- 대응 테이블: `tickets`
- 설명: 분실·유출된 QR 토큰을 새로 발급한다. `ISSUED` 상태의 티켓만 가능하다.
- 요청 파라미터: `ticketId`(path)
- 응답(200): `{ ticketId, qrToken }`
- 에러: `TICKET_NOT_FOUND`(404) / `INVALID_STATE_TRANSITION`(409, `ISSUED`가 아닌 티켓)
- 프론트 계약: 재발급 즉시 기존 QR은 무효화되므로, 관리자 화면에서 새 QR을 재렌더링해 사용자에게 재전달해야 한다.
- 동시성/멱등 보장: 새 토큰은 UUID + 중복 존재 체크(최대 3회 재시도)로 유일성만 보장한다. 재발급 자체에 대한 락은 없다.

### 9.19 티켓 통합 검색(회원·날짜·상태)

- Method / URI: `GET /api/v1/admin/tickets`
- 인증: JWT + ADMIN
- 대응 테이블: `tickets`
- 설명: 회원, 경기 날짜 범위, 티켓 상태를 조합해 검색한다. 모든 조건은 선택이며 미지정 시 전체 조회한다. `REFUND_FAILED` 목록 조회(기존 §9.15)도 `status=REFUND_FAILED`로 이 API를 사용한다.
- 요청 파라미터(쿼리):

| 파라미터 | 필수 | 설명 |
| --- | --- | --- |
| `userId` | 선택 | 회원 ID |
| `status` | 선택 | 티켓 상태 |
| `gameDateFrom` | 선택 | 경기일 시작(yyyy-MM-dd) |
| `gameDateTo` | 선택 | 경기일 끝(yyyy-MM-dd) |
| `page`/`size`/`sort` | 선택 | 표준 Pageable |
- 응답(200): `PageResponse<AdminUserTicketResponse>`
- 에러: `USER_NOT_FOUND`(404) / `userId` 지정했으나 존재하지 않는 회원
- 동시성/멱등 보장: 조회 전용(`readOnly`), 해당 없음.

> §9.14(회원별 티켓 조회)와 §9.15(환불 실패 티켓 조회)는 이 API의 `userId`/`status` 파라미터 조합으로 대체 가능하다. 두 절은 하위 호환을 위해 남겨두되, 신규 연동은 9.18을 사용한다.
> 

### 9.20 경기별 일괄 강제 취소

- Method / URI: `POST /api/v1/admin/tickets/games/{gameId}/cancel-bulk`
- 인증: JWT + ADMIN
- 대응 테이블: `tickets`, `payment_cancels`, `payments`, `game_seats`, `orders`(티켓 1건당 §7.5·§9.13과 동일 경로를 반복 실행)
- 설명: 특정 경기의 `ISSUED` 티켓 전체를 관리자 직권 취소 파이프라인에 태운다.
- 요청 파라미터: `gameId`(path)
- 요청 바디: `{ reason: string }`
- 응답(200): `{ totalCount, successCount, failureCount, results: [{ ticketId, success, message }] }`
- 에러: 이 API 자체는 개별 티켓 실패를 HTTP 에러로 올리지 않고 200 응답의 `results`에 성공/실패를 함께 담아 반환한다. 대상 `gameId`에 `ISSUED` 티켓이 없으면 `totalCount: 0`으로 정상 응답한다.
- 프론트 계약: `results`를 순회해 실패 건만 별도로 보여주고 재시도를 유도한다.
- 동시성/멱등 보장: 티켓 1건당 독립된 트랜잭션으로 처리돼 한 건 실패가 나머지에 영향을 주지 않는다. 같은 경기에 대한 동시 중복 호출은 각 티켓의 `requestRefund()` 상태 가드(`ISSUED` 아니면 예외)로 개별적으로 안전하게 처리된다.

### 9.21 관리자 경기별 대기열 현황 조회

- `GET /api/v1/admin/queues/games/{gameId}/overview`
- 인증: JWT(ADMIN)
- 대응 테이블: `games`, `admission_tokens`, `Redis ZSet`
- 설명:
    - 특정 경기의 예매 상태와 현재 대기 인원을 조회한다.
    - 사용할 수 있는 `ACTIVE` Queue-Token 수와 오늘 발급된 Queue-Token 수를 조회한다.
    - Redis와 DB를 순차 조회하므로 동일 트랜잭션 시점의 스냅샷이 아니라 조회 시점의 운영 현황을 반환한다.
- 응답 (200):
    
    ```json
    {
      "success": true,
      "errorCode": null,
      "message": "관리자 대기열 현황 조회 완료",
      "data": {
        "gameId": 10,
        "bookingStatus": "OPEN",
        "waitingCount": 25,
        "usableAdmissionCount": 4,
        "admittedToday": 120,
        "collectedAt": "2026-09-12T12:00:00"
      }
    }
    ```
    
    - `waitingCount`: Redis 대기열에 현재 등록된 인원 수
    - `usableAdmissionCount`: 만료되지 않은 ACTIVE 토큰 중 좌석 탐색이 완료되었거나 좌석 탐색 가능 시간이 남아 있는 토큰 수
    - `admittedToday`: 오늘 발급된 Queue-Token 수
    - `collectedAt`: 현황을 조회한 시간
- 에러:
    - `FORBIDDEN`(403) / ADMIN 권한이 없는 경우
    - `INVALID_REQUEST`(400) / `gameId` 형식이 올바르지 않은 경우
    - `GAME_NOT_FOUND`(404) / 경기 정보를 찾을 수 없는 경우
- 프론트 계약:
    - 관리자 대시보드의 경기별 현재 대기열 현황 표시에 사용합니다.

### 9.22 관리자 경기별 입장 지표 조회

- `GET /api/v1/admin/queues/games/{gameId}/admission-metrics`
- 인증: JWT(ADMIN)
- 대응 테이블: `admission_tokens`
- 설명:
    - 특정 경기에서 실제 발급된 Queue-Token 수를 일별·주별·월별로 집계한다.
    - `AdmissionToken.issuedAt`을 기준으로 집계한다.
    - 조회 기간 중 데이터가 없는 구간도 발급 수 0으로 반환한다.
    - `from`과 `to` 날짜를 모두 포함하며 조회 기간은 최대 366일이다.
    - `WEEKLY`는 월요일을 집계 구간의 시작일로 사용한다.
- 요청 파라미터:

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `period` | String | Y | 집계 단위(`DAILY`, `WEEKLY`, `MONTHLY`) |
| `from` | LocalDate | Y | 조회 시작일(`yyyy-MM-dd`) |
| `to` | LocalDate | Y | 조회 종료일(`yyyy-MM-dd`) |
- 응답 (200):
    
    ```json
    {
      "success": true,
      "errorCode": null,
      "message": "관리자 대기열 입장 지표 조회 완료",
      "data": {
        "gameId": 10,
        "period": "DAILY",
        "from": "2026-09-01",
        "to": "2026-09-03",
        "series": [
          { "bucket": "2026-09-01", "admittedCount": 2 },
          { "bucket": "2026-09-02", "admittedCount": 0 },
          { "bucket": "2026-09-03", "admittedCount": 1 }
        ]
      }
    }
    ```
    
    - `series[].bucket`: DAILY는 `yyyy-MM-dd`, WEEKLY는 해당 주 월요일의 `yyyy-MM-dd`, MONTHLY는 `yyyy-MM` 형식
    - `series[].admittedCount`: 해당 구간에 발급된 Queue-Token 수
- 에러:
    - `FORBIDDEN`(403) / ADMIN 권한이 없는 경우
    - `INVALID_REQUEST`(400) / `gameId` 또는 쿼리 파라미터 형식이 올바르지 않은 경우
    - `QUEUE_ADMISSION_METRIC_SEARCH_CONDITION_INVALID`(400) / 조회 기간이 역전되었거나 366일을 초과한 경우
    - `GAME_NOT_FOUND`(404) / 경기 정보를 찾을 수 없는 경우
- 프론트 계약: 관리자 대시보드의 경기별 입장 추이 차트에 사용한다.

### 9.23 관리자 경기 목록 조회·검색

- Method / URI: `GET /api/v1/admin/games`
- 인증: JWT + ADMIN
- 대응 테이블: `games`(+ `teams`, `stadiums` 조인)
- 설명: 관리자가 경기일 범위·구장·구단·예매 상태 조건으로 경기 목록을 조회하고 현재 예매 상태를 확인한다. 상태 전이(PATCH)는 포함하지 않으며 §9.12를 그대로 사용한다.
- 요청 파라미터: `homeTeamId`, `awayTeamId`, `stadiumId`(관리자 전용 추가 조건), `from`, `to`, `bookingStatus`, `page`, `size`, `sort`(기본 `gameAt,asc` / 허용: `gameAt`, `bookingOpenAt`, `bookingCloseAt`, `id`)
- 응답(200): §2.1(공개 경기 목록 조회)과 동일한 스키마. `content[].bookingStatus`, `bookingOpenAt`, `bookingCloseAt` 포함
- 에러:
    - `INVALID_REQUEST`(400) / `from`이 `to`보다 늦은 경우 등 파라미터 오류
    - `FORBIDDEN`(403) / ADMIN 아닌 사용자
- 프론트 계약: 구장·구단 조건은 프론트 드롭다운에서 ID로 매핑해 전달한다(텍스트 검색 파라미터 없음).
- 비고: 신규 에러 코드 없음(기존 `INVALID_REQUEST`/`FORBIDDEN` 재사용). ERD 변경 없음(`idx_games_stadium_game_at` 인덱스로 `stadiumId` 필터 충분).

### 9.24 경기 좌석 상태 요약 조회

- Method / URI: `GET /api/v1/admin/games/{gameId}/seats/summary`
- 인증: JWT + ADMIN
- 대응 테이블: `game_seats`
- 대응 서비스: `SeatQueryService.summarize()`(기존 좌석 조회 서비스에 메서드 추가, 신규 서비스 없음)
- 설명: 경기 전체 기준 상태별(`AVAILABLE`/`HELD`/`SOLD`/`BLOCKED`) 좌석 수 합계를 반환한다. 구역별 세분화는 포함하지 않는다(구역 이름·가격은 §4.2 구역 목록 조회 API 참고).
- 응답(200):
    
    ```sql
    {
        "success": true,
        "errorCode": null,
        "message": "좌석 재고 요약 조회",
        "data": {
            "available": 18240,
            "held": 312,
            "sold": 6410,
            "blocked": 38
        }
    }
    ```
    
- 에러: `FORBIDDEN`(403) / `GAME_NOT_FOUND`(404) / `SEAT_INVENTORY_NOT_OPENED`(409, 좌석 재고 미오픈 경기 조회)
- 프론트 계약: 관리자 화면 상단 요약 카드 렌더링용. 4개 값의 합은 해당 경기의 전체 좌석 재고 수와 일치해야 한다.

### 9.25 관리자 전용 좌석 그리드 조회

- Method / URI: `GET /api/v1/admin/games/{gameId}/seats`
- 인증: JWT + ADMIN(Queue-Token 불요)
- 대응 테이블: `game_seats`(+ `seats`, `seat_zones` 조인)
- 대응 서비스: `SeatQueryService.getSeats()`(공개 §4.1과 동일 메서드 재사용, 신규 서비스 없음)
- 설명: 관리자가 판매 차단 대상을 고르기 위해 경기의 개별 좌석 상태를 조회한다. 공개 API(§4.1, `GET /api/v1/games/{gameId}/seats`)와 조회 로직은 동일하나, 부록 B 인증 요약표상 §4.1이 요구하는 Queue-Token을 요구하지 않는다. 관리자는 대기열을 거치지 않으므로 Queue-Token을 발급받을 수 없어 별도 경로로 노출한다.
- 요청 파라미터:

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `zoneId` | Long | N | 구역 필터 |
| `status` | Enum | N | 상태 필터(`AVAILABLE`/`HELD`/`SOLD`/`BLOCKED`) |
- 응답(200): §4.1과 동일한 좌석 배열 스키마(`gameSeatId`, `zoneId`, `zoneName`, `grade`, `seatBlock`, `seatRow`, `seatNumber`, `price`, `status`)
- 에러: `FORBIDDEN`(403) / `GAME_NOT_FOUND`(404) / `SEAT_INVENTORY_NOT_OPENED`(409)
- 프론트 계약: 관리자 좌석 차단 화면의 좌석 그리드 렌더링용. `status=AVAILABLE`로 필터해 차단 가능 좌석만 선택지로 노출할 수 있다.

> 부록 B 인증·입장 토큰 요약표에 9.24 행을 별도로 추가하고, "9.24는 좌석 조회 목적상 §4.1과 로직을 공유하되 Queue-Token은 요구하지 않는다"는 점을 명시한다.
> 

### 9.26 예약·선점 상태 관리 목록 조회

- Method / URI: `GET /api/v1/admin/games/{gameId}/reservations`
- 인증: JWT + ADMIN
- 대응 테이블: `reservations`, `reservation_seats`(조회 전용, 상태 변경 없음)
- 설명: 관리자가 경기별 예약·선점 현황을 상태(전체/`HOLDING`/`CONFIRMED`/`EXPIRED`)별로 페이지네이션 조회한다. 강제 해제 등 상태 변경 기능은 제공하지 않으며, 만료 처리는 기존 `HoldExpiryScheduler`가 그대로 담당한다.
- 요청 파라미터:

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `status` | Enum(`HOLDING`/`CONFIRMED`/`EXPIRED`) | N | 없음(전체 조회) | 미지정 시 전체 상태 조회. `CANCELED`는 이번 필터 옵션에 포함하지 않음 |
| `page` | int | N | 0 | 페이지 번호(0-base) |
| `size` | int | N | 20 | 페이지 크기 |
| `sort` | string | N | `createdAt,DESC` | 허용 값: `createdAt`, `holdExpiresAt` |
- 응답(200):
    
    ```sql
    {
        "success": true,
        "errorCode": null,
        "message": "예약·선점 상태별 목록 조회 완료",
        "data": {
            "content": [
                {
                    "reservationId": 1024,
                    "reservationNo": "RSV-20260913-a1b2c3",
                    "userId": 58,
                    "status": "HOLDING",
                    "remainingSeconds": 252,
                    "seats": [
                        { "gameSeatId": 3301, "seat": "1루 블루석 A-3-12", "price": 15000 },
                        { "gameSeatId": 3302, "seat": "1루 블루석 A-3-13", "price": 15000 }
                    ]
                }
            ],
            "pageNumber": 0,
            "pageSize": 20,
            "totalElements": 37,
            "totalPages": 2,
            "isFirst": true,
            "isLast": false
        }
    }
    ```
    
- 에러:
    - `INVALID_REQUEST`(400) / `status` 파라미터가 `ReservationStatus`로 변환 불가한 값인 경우
    - `UNAUTHORIZED`(401) / 미인증 요청
    - `FORBIDDEN`(403) / ADMIN 권한 없음
    - `GAME_NOT_FOUND`(404) / 존재하지 않는 `gameId`
- 프론트 계약:
    - `remainingSeconds`는 `status=HOLDING`인 행에만 값이 채워지고, 그 외 상태는 `null`이다. 프론트는 null 여부로 "잔여시간 컬럼 표시 여부"를 분기하면 된다.
    - `seats`는 예약당 최대 2건까지 배열로 내려간다(1인당 2매 제한 정책). 프론트는 단일 좌석 문자열이 아니라 배열 렌더링을 전제해야 한다.
    - 관리자 화면 필터 탭은 전체/`HOLDING`/`CONFIRMED`/`EXPIRED` 4개만 노출한다(`CANCELED` 탭 없음). `CANCELED`는 API 자체는 거부하지 않으나(파라미터로 넘기면 조회는 됨) 화면 탭에는 포함하지 않는다.
- 동시성/멱등 보장: 해당 없음 — 조회 전용(GET) API로 상태를 변경하지 않으며, 동시 요청 간 부작용이 없다.

---

## 비고

- `admin_audit_logs` 여부

---
