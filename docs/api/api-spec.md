# API 명세서

## 0. 공통 규약

### 0.1 기본 규칙

| 항목 | 값 |
| --- | --- |
| Base URL | `/api/v1` |
| 인증 방식 | `Authorization: Bearer {accessToken}` (JWT) |
| 좌석 선점 추가 인증 | `Queue-Token: {입장 토큰}` 헤더 필수 |
| Content-Type | `application/json; charset=UTF-8` |
| 날짜 형식 | ISO-8601 `yyyy-MM-dd HH:mm:ss` 
(서비스·DB·API 전 구간 `Asia/Seoul` 통일, DB는 `DATETIME(6)`) |
| 페이지네이션 | `?page=0&size=20` (Offset 기반, 0-based) |
| 정렬 | `?sort=field,asc` / `?sort=field,desc` |

### 0.2 식별자(ID) 규칙

- 경로 변수(path) 는 숫자 PK를 사용한다.
    - 예: `/reservations/{reservationId}` → `reservationId = reservations.id (BIGINT)`
- 응답 바디에는 숫자 PK(`xxxId`)와 업무번호(`xxxNo`)를 함께 노출한다.
    - 업무번호는 화면 표기·고객 문의용이다.
- 업무번호 표기 예시(형식은 구현 시 확정):
    - `RSV-20260711-000001`, `ORD-20260711-000001`, `PAY-20260711-000001`, `TKT-20260711-000001`
- 사용자에게 노출하는 "예매번호"는 `orderNo`를 사용한다.
    - `reservationNo`는 내부 선점 추적용이며 화면에 노출하지 않는다.
- 좌석 식별자 주의:
    - 예매·선점·주문은 물리 좌석(`seats.id`)이 아니라 경기 좌석 재고(`game_seats.id`) 기준으로 한다.
    - 요청/응답의 좌석 식별자는 `gameSeatId`(= `game_seats.id`)이며, 다좌석은 `gameSeatIds`(정수 리스트)로 전달한다.
- 환불 식별자 주의:
    - 환불의 단위는 좌석이므로 취소 대상은 `ticketId`로 지정한다.
    - 예매번호(`orderNo`)는 취소 실행 키가 아니다. 조회·CS 응대용이다.

### 0.3 공통 성공 / 에러 응답

```json
// 성공
{
    "success": true,
    "errorCode": null,
    "message": "요청이 성공했습니다.",
    "data": {}
}

// 실패
{
    "success": false,
    "errorCode": "SEAT_ALREADY_HELD",
    "message": "이미 선점된 좌석입니다.",
    "data": null
}
```

**컨트롤러 응답 규약** 

- 모든 컨트롤러의 성공 응답은 `ResponseEntity<ApiResponse<T>>` 형태로 반환한다.
- 상태 코드는 `.status(HttpStatus.XXX).body(...)`로 표기한다. (200도 `HttpStatus.OK`로 명시)
- 에러 응답은 `GlobalExceptionHandler`가 동일하게 `ResponseEntity<ApiResponse<Void>>` 형태로 처리한다.
- 모든 응답은 `success`/`errorCode`/`message`/`data` 4개 필드를 항상 포함한다.
`ApiResponse<T>` 래퍼가 단일 스키마를 보장하므로 성공 응답에서도 `errorCode`를 생략하지 않는다.
- 성공 시 `errorCode = null`, 실패 시 `data = null`이다.
- 필드 순서는 `success` → `errorCode` → `message` → `data`로 통일한다.
- SSE(#3.3)는 스트리밍이므로 이 래퍼를 사용하지 않는다.

**페이징 응답 표준** 

- 목록 조회 응답의 `data`는 아래 `PageResponse` 구조로 통일한다.
- Spring `Page` 원형(raw)이나 커스텀 평면 구조를 직접 반환하지 않는다.
- 배열은 반드시 `content` 키에 담는다(`tickets`, `users` 등 도메인별 키 사용 금지).

```json
{
    "content": [ ... ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1,
    "isFirst": true,
    "isLast": true
}
```

### 0.4 에러 코드 표

[04. 에러 코드 표](https://app.notion.com/p/04-b2aac7091a7482428f8d01d8af2e7a61?pvs=21)

### 0.5 상태값 요약

| 도메인(테이블) | 컬럼 | 상태값 |
| --- | --- | --- |
| 경기(games) | booking_status | SCHEDULED / OPEN / CLOSED / CANCELLED |
| 경기 좌석 재고(game_seats) | status | AVAILABLE / HELD / SOLD / BLOCKED |
| 대기열(queue_entry_histories) | status | WAITING / ADMITTED / CANCELED |
| 입장 토큰(admission_tokens) | status | ACTIVE / USED / EXPIRED |
| 예약(reservations) | status | HOLDING / CONFIRMED / CANCELED / EXPIRED |
| 주문(orders) | status | CREATED / PAID / CANCELED / EXPIRED |
| 결제(payments) | status | READY / APPROVED / FAILED / PARTIALLY_CANCELED / CANCELED |
| 티켓(tickets) | status | ISSUED / REFUND_PENDING / REFUND_FAILED / REFUNDED / USED_ENTERED / USED_NO_SHOW |

| 결제(payments) | method | MOCK / TOSS |
| --- | --- | --- |

### 0.6 권한 요약

| 그룹 | 인증 | 비고 |
| --- | --- | --- |
| 회원가입/로그인/토큰 재발급 | 불필요 |  |
| 경기 목록/상세 조회 | 불필요 | 공개  |
| 대기열/주문/결제/티켓 | JWT 필요 |  |
| 좌석 조회·선점(HOLD) | JWT + Queue-Token 
+ 본인인증(`is_verified=true`) | 입장 토큰 검증(대기열 우회 차단)
+ 예매 게이트 |
| 관리자(기준 데이터·경기·좌석 오픈) | JWT + role=ADMIN |  |
| 구장 혼잡도 조회 | 불필요 | 공개, 비로그인 허용 |

---

## 1. 인증 (auth)

> `users`, `refresh_tokens`
> 

### **1.1 회원가입**

- `POST /api/v1/auth/signup`
- 인증: 불필요
- 대응 테이블: `users`
- 설명: 이메일·비밀번호로 회원 생성.
    - ERD의 `users`(email, password, name, nickname, phone, role, status)에 매핑.
- 요청 바디:
    
    ```json
    {
        "email": "user@example.com",
        "password": "********",
        "name": "홍길동",
        "phone": "010-1234-5678"
    }
    ```
    
- 응답(201):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "회원가입 완료",
        "data": {
            "userId": 1,
            "email": "user@example.com",
            "role": "USER",
            "status": "ACTIVE"
        }
    }
    ```
    
- 에러:
    - `INVALID_REQUEST`(400)
    - `DUPLICATE_LOGIN_ID`(409, email/nickname/phone UK 위반)

### **1.2 로그인**

- `POST /api/v1/auth/login`
- 인증: 불필요
- 대응 테이블: `users`, `refresh_tokens`
- 요청 바디:
    
    ```json
    {
        "email": "user@example.com",
        "password": "********"
    }
    ```
    
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "로그인 성공",
        "data": {
            "accessToken": "eyJ...",
            "refreshToken": "eyJ...",
            "role": "USER",
            "expiresIn": 3600
        }
    }
    ```
    
- 에러:
    - `UNAUTHORIZED`(401)
    - `USER_INACTIVE`(403, 정지·탈퇴 계정)

### **1.3 토큰 재발급**

- `POST /api/v1/auth/reissue`
- 인증: 불필요(refreshToken 사용)
- 요청 바디:
    
    ```json
    {
        "refreshToken": "eyJ..."
    }
    ```
    
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "토큰 재발급 완료",
        "data": {
            "accessToken": "eyJ..."
        }
    }
    ```
    
- 에러: `UNAUTHORIZED`(401)

### 1.4 로그아웃

- `POST /api/v1/auth/logout`
- 인증: JWT
- 설명: 현재 로그인한 사용자의 `refresh_tokens` 레코드를 파기한다.
- 응답(200):
    
    ```json
    { 
    		"success": true, 
    		"errorCode": null, 
    		"message": "로그아웃 완료", 
    		"data": null 
    }
    ```
    
- 에러: `UNAUTHORIZED`(401)

### 1.5 관리자 로그인

- `POST /api/v1/auth/admin/login`
- 인증: 불필요
- 대응 테이블: `users`(role=ADMIN 검증)
- 설명: 관리자 전용 로그인 엔드포인트. 일반 로그인과 분리돼 있다.
- 요청 바디:
    
    ```json
    { 
    		"email": "admin@example.com", 
    		"password": "********" 
    }
    ```
    
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "관리자 로그인 성공 완료",
        "data": {
            "grantType": "Bearer",
            "accessToken": "eyJ...",
            "refreshToken": "eyJ...",
            "userId": 1,
            "email": "admin@example.com",
            "name": "관리자",
            "role": "ADMIN"
        }
    }
    ```
    
- 에러: `INVALID_REQUEST`(400) / `UNAUTHORIZED`(401) / `ADMIN_ACCESS_REQUIRED`(403, 일반 계정으로 시도)

### 1.6 프로필 조회

- `GET /api/v1/users/me`
- 인증 : JWT
- 설명 : 현재 로그인한 사용자의 프로필 정보를 조회한다.
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "내 정보 조회 완료",
        "data": {
            "id": 1,
            "email": "user@example.com",
            "name": "홍길동",
            "nickname": "길동이",
            "phone": "010-1234-5678",
            "isVerified": false
        }
    }
    ```
    
- 에러: `UNAUTHORIZED`(401) / `USER_NOT_FOUND`(404)

### 1.7 회원정보 수정

- `PUT /api/v1/users/me`
- 인증:  JWT
- 설명: 회원의 이름과 전화번호 정보를 수정한다.
- 요청 바디:
    
    ```json
    {
    	  "name": "홍길동",
    	  "phone": "010-1234-5678"
    }
    ```
    
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "회원정보 수정 완료",
        "data": null
    }
    ```
    
- 에러: `INVALID_REQUEST`(400) / `UNAUTHORIZED`(401) / `DUPLICATE_LOGIN_ID`(409)

### 1.8 비밀번호 변경

- `PATCH /api/v1/users/me/password`
- 인증: JWT
- 설명: 현재 비밀번호를 BCrypt로 대조한 뒤, 통과 시에만 새 비밀번호를 암호화해서 교체한다.
- 요청 바디:

```json
{
    "currentPassword": "Password123!",
    "newPassword": "NewPassword123!"
}
```

- 응답 (200):

```json
{
    "success": true,
    "errorCode": null,
    "message": "비밀번호 변경 완료",
    "data": null
}
```

- 에러: `INVALID_REQUEST`(400) / `UNAUTHORIZED`(401, 현재 비밀번호 불일치)

### 1.9 본인 인증 완료

- `POST /api/v1/users/verification`
- 인증: JWT
- 설명: 클라이언트가 PortOne SDK로 획득한 `impUid`만 서버에 전달하면, 서버가 PortOne API를 직접 호출해 CI·실명·전화번호를 조회한 뒤 갱신한다.
- 요청 바디:
    
    ```json
    {
        "impUid": "imp_1234567890"
    }
    ```
    
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "본인인증 완료",
        "data": null
    }
    ```
    
- 에러:
    - `INVALID_REQUEST`(400)
    - `UNAUTHORIZED`(401)
    - `VERIFICATION_DUPLICATE_CI`(409, `users.ci` 중복)
    - `VERIFICATION_FAILED`(PortOne 조회 실패)

### 1.10 회원 탈퇴

- `DELETE /api/v1/users/me`
- 인증: JWT
- 설명:
    - 회원 상태를 `DELETED`로 바꾸고, `email`은 `"deleted_" + id + "_" + email`로, `name`/`nickname`은 `"탈퇴회원"`으로, `phone`은 `"000-0000-0000"`으로 마스킹하며, `ci`/`isVerified`를 초기화한다.
    - 탈퇴 전 `tickets.status = ISSUED`인 티켓이 1건이라도 있으면 무조건 차단한다.
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "회원 탈퇴 완료",
        "data": null
    }
    ```
    
- 에러
    - `UNAUTHORIZED`(401)
    - `TICKET_EXISTS_ON_WITHDRAWAL`(409) / 정산이 완료되지 않은 티켓이 남아 있는 경우
- 프론트 계약:
    - 409 응답 시 마이페이지로 유도하고 환불 대상 티켓을 안내한다.

### 1.11 소셜 로그인 (카카오)

- Method / URI:
    - `GET /oauth2/authorization/kakao` (로그인 시작 — 카카오 동의 화면으로 리다이렉트)
    - `GET /login/oauth2/code/kakao` (카카오 콜백 — Spring Security `oauth2Login` 필터가 내부 처리, 애플리케이션 컨트롤러 없음)
- 인증: 불필요
- 대응 테이블: `users`(provider, provider_id, email, name, role=USER, status=ACTIVE, is_verified=false)
- 지원 제공자: kakao만 지원한다.
    - `CustomOAuth2UserService`가 `registrationId`를 검사해 `kakao`가 아니면 `OAuth2AuthenticationException("Unsupported provider")`를 던진다.
- 설명:
    1. 프론트가 사용자를 `GET /oauth2/authorization/kakao`로 이동시킨다.
    2. 카카오 동의 후 `redirect-uri`(`{baseUrl}/login/oauth2/code/kakao`)로 인가 코드가 돌아온다.
    3. Spring Security가 토큰을 교환하고 `CustomOAuth2UserService.loadUser()`를 호출한다.
    4. `provider`+`provider_id`로 기존 회원을 조회한다.
        - 있으면 그대로 사용(단, `status=DELETED`/`SUSPENDED`면 `OAuth2AuthenticationException`으로 거부).
        - 없으면 신규 생성한다. 이메일은 `{providerId}@kakao.com` 형태의 가상 이메일을 사용하고(카카오 실제 이메일과 충돌 방지), `role=USER`로 고정한다(소셜 로그인으로 관리자 자동 승격 불가).
    5. `OAuth2AuthenticationSuccessHandler`가 JWT Access/Refresh 토큰을 발급하고 Redis에 Refresh Token을 14일 TTL로 저장한다.
    6. `oauth2.redirect.frontend-url` 설정값으로 지정된 프론트 URL에 토큰을 쿼리 파라미터로 실어 302 리다이렉트한다. 공통 `ApiResponse` 래퍼를 사용하지 않는다.
- 응답: JSON 바디가 아니라 리다이렉트다.
    
    ```
    302 Found
    Location: {oauth2.redirect.frontend-url}?accessToken=eyJ...&refreshToken=eyJ...&isVerified=false
    ```
    
- 에러:
    - `OAuth2AuthenticationException` / 지원하지 않는 provider, 탈퇴·정지 계정으로 로그인 시도 — Spring Security `oauth2Login().failureHandler(oAuth2AuthenticationFailureHandler)`가 처리하며, 이 경우도 JSON 에러가 아니라 실패 리다이렉트로 응답한다.
- 프론트 계약:
    - 팝업 또는 전체 리다이렉트로 `/oauth2/authorization/kakao`를 연다.
    - 콜백 후 도착하는 리다이렉트 URL의 쿼리 파라미터에서 `accessToken`/`refreshToken`/`isVerified`를 파싱해 저장한다.
    - `isVerified=false`이면 본인인증(1.9) 화면으로 이어서 유도한다.
        - 카카오 로그인은 본인인증을 대신하지 않는다.

---

## 2. 경기 **(games)**

> `games`, `teams`, `stadiums`
> 

### **2.1 경기 목록 조회**

- `GET /api/v1/games`
- 인증: 불필요
- 대응 테이블: `games` (+ `teams`, `stadiums` 조인)
- 요청 파라미터
    
    
    | 파라미터 | 타입 | 필수 | 기본값 | 설명 |
    | --- | --- | --- | --- | --- |
    | `homeTeamId` | Long | N | - | 홈팀 ID 필터 (미존재 ID는 에러가 아니라 빈 결과) |
    | `awayTeamId` | Long | N | - | 원정팀 ID 필터 |
    | `from` | Date | N | - | 경기일 검색 시작일(해당일 00:00:00 포함) |
    | `to` | Date | N | - | 경기일 검색 종료일(해당일 23:59:59까지 포함
    내부적으로 `to+1일 00:00` 미만) |
    | `bookingStatus` | Enum | N | - | `SCHEDULED`/`OPEN`/`CLOSED`/`CANCELLED` |
    | `page` | int | N | `0` | 0-based 페이지 번호 |
    | `size` | int | N | `20` | 페이지 크기 |
    | `sort` | string | N | `gameAt,asc` | 허용 필드: `gameAt`, `bookingOpenAt`, `bookingCloseAt`, `id` |
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "경기 목록 조회 성공",
        "data": {
            "content": [
                {
                    "gameId": 1,
                    "title": "LG vs 한화",
                    "homeTeam": { "teamId": 1, "name": "LG" },
                    "awayTeam": { "teamId": 2, "name": "한화" },
                    "stadium": { "stadiumId": 1, "name": "잠실야구장" },
                    "gameAt": "2026-07-11 18:30:00",
                    "bookingOpenAt": "2026-07-04 14:00:00",
                    "bookingCloseAt": "2026-07-11 18:30:00",
                    "bookingStatus": "OPEN"
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
    
- 에러
    - `INVALID_REQUEST`(400)

### **2.2 경기 상세 조회**

- `GET /api/v1/games/{gameId}`
- 인증: 불필요
- 대응 테이블: `games` (+ `teams`, `stadiums` 조인)
- 응답(200): 경기 1건의 상세(홈/원정 팀, 구장, 일시, 예매 오픈/마감, `bookingStatus`, `title`)
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "경기 상세 조회 성공",
        "data": {
            "gameId": 1,
            "title": "LG vs 한화",
            "homeTeam": { "teamId": 1, "name": "LG" },
            "awayTeam": { "teamId": 2, "name": "한화" },
            "stadium": {
                "stadiumId": 1,
                "name": "잠실야구장",
                "address": "서울특별시 송파구 올림픽로 25",
                "totalCapacity": 25000
            },
            "gameAt": "2026-07-11 18:30:00",
            "bookingOpenAt": "2026-07-04 14:00:00",
            "bookingCloseAt": "2026-07-11 18:30:00",
            "bookingStatus": "OPEN"
        }
    }
    ```
    
- 에러
    - `INVALID_REQUEST`(400): `gameId` 타입 오류(숫자 아님)
    - `GAME_NOT_FOUND`(404): 미존재 `gameId`

---

## **3. 대기열 (queue)**

> 테이블:  `queue_entry_histories`, `admission_tokens`
사용 요소: `Kafka`, `Redis ZSet`, `Redisson`
> 

> 
> 
> 
> 동작 흐름:
> 
> 1. 사용자가 대기열 진입 API를 호출한다.
> 2. 서버가 경기와 사용자의 존재 여부를 확인한다.
> 3. 서버가 Kafka에 대기열 진입 요청 이벤트를 발행한다.
> 4. Kafka 발행에 성공하면 클라이언트에 `202 Accepted`를 반환한다.
> 5. Kafka Consumer가 이벤트를 처리해 DB 대기 이력과 Redis ZSet을 등록한다.
> 6. 자동 입장 스케줄러가 경기별 대기열 앞쪽 사용자를 주기적으로 처리한다.
> 7. 입장 허용 시 DB 상태를 `ADMITTED`로 변경하고 21분간 유효한 Queue-Token을 발급한다.
> 8. 취소된 대기 이력은 새로운 요청 시간으로 갱신해 대기열에 다시 등록한다.
> 9. 다른 경기에서 대기 중이거나 활성 Queue-Token이 있는 사용자는 새로운 대기열에 등록하지 않는다.
> 10. 마지막 SSE 연결이 종료되면 재연결 유예시간을 적용한다.
> 11. 유예시간 안에 재연결되지 않으면 대기 이력과 활성 Queue-Token을 취소한다.

### **3.1 대기열 진입 / 토큰 발급**

- `POST /api/v1/queues/{gameId}/enter`
- 인증: JWT
- 대응 테이블: Kafka Consumer 처리 후 `queue_entry_histories` 등록
- 설명:
    - 경기와 사용자의 존재 여부를 확인한 후 Kafka에 대기열 진입 요청 이벤트를 발행한다.
    - `gameId`를 Kafka 메시지 Key로 사용한다.
    - Kafka 브로커 발행이 성공하면 `202 Accepted`를 반환한다.
    - Consumer가 비동기로 처리하므로 응답 시점에는 DB와 Redis 등록이 완료되지 않을 수 있다.
    - 활성 입장 토큰이 있는 사용자는 Consumer 처리 과정에서 다시 대기열에 등록하지 않는다.
    - 동일 경기 · 사용자의 기존 `WAITING` 이력이 있으면 새 이력을 만들지 않고 누락된 Redis만 복구 시킨다.
    - 기존 대기 이력이 `CANCELED` 상태이면 새로운 이력을 생성하지 않고 기존 이력을 `WAITING` 상태로 변경한다.
    - 재진입 시 새로운 요청 시간으로 `enteredAt`을 갱신하고 기존 `admittedAt`과 `canceledAt`을 초기화한다.
    - Redis ZSet 점수를 새로운 요청 시간으로 갱신하여 대기열 마지막 순서로 재등록한다.
    - 다른 경기에서 `WAITING` 상태로 대기 중인 사용자는 새로운 경기 대기열에 등록하지 않는다.
- 응답(202):
    
    ```json
    { 
      	"success": true,
      	"errorCode": null,
      	"message": "대기열 진입 요청 접수",
        "data": null
    }
    ```
    
- 에러:
    - `UNAUTHORIZED`(401) / JWT가 없거나 유효하지 않은 경우
    - `INVALID_REQUEST`(400) / gameId 형식이 올바르지 않은 경우
    - `GAME_NOT_FOUND`(404) / 경기 정보를 찾을 수 없는 경우
    - `USER_NOT_FOUND`(404) / 사용자 정보를 찾을 수 없는 경우
    - `QUEUE_EVENT_PUBLISH_FAILED`(503) / Kafka 대기열 진입 이벤트 발행에 실패한 경우
- 동시성/멱등 보장:
    - Kafka 재전달이나 동일 이벤트 재처리 시 새로운 대기 이력을 중복 생성하지 않는다.
    - 기존 상태가 WAITING이면 Redis 등록 누락 여부만 복구한다.
    - 활성 Queue-Token이 있다면 다시 대기열에 등록하지 않는다.
    - 취소 요청보다 늦게 발행된 새로운 진입 요청만 재진입으로 처리한다.
    - 대기 취소와 다른 경기 진입이 동시에 처리되지 않도록 사용자 정보를 기준으로 동시성을 제어한다.

### 3.2 내 대기 상태 조회

- `GET /api/v1/queues/{gameId}/me`
- 인증: JWT
- 대응 테이블: `queue_entry_histories` `admission_tokens` `Redis ZSet`
- 설명:
    - 활성 Queue-Token이 있으면 ADMITTED 상태를 반환한다.
    - 활성 토큰이 없으면 Redis ZSet에서 현재 사용자의 대기 순번을 조회한다.
    - 현재 순번과 자동 입장 정책을 기준으로 예상 대기시간을 계산해 반환한다.
    - 한 번에 최대 20명을 3초 간격으로 입장 처리하는 기준으로 `ceil(현재 순번 / 20) × 3초`로 계산한다.
- 응답(200/대기 중):
    
    ```json
    { 
      	"success": true,
      	"errorCode": null,
      	"message": "대기열 상태 조회",
        "data": {
    		    "rank": 10,
    	      "estimatedWaitSeconds": 3,
    	      "queueStatus": "WAITING",
    	      "admitted": false
        }
    }
    ```
    
- 응답(200/입장 허용):
    
    ```json
    { 
      	"success": true,
      	"errorCode": null,
      	"message": "대기열 상태 조회",
        "data": {
    		    "rank": 0,
    	      "estimatedWaitSeconds": 0,
    	      "queueStatus": "ADMITTED",
    	      "admitted": true
        }
    }
    ```
    
- 에러:
    - `UNAUTHORIZED`(401) / JWT가 없거나 유효하지 않은 경우
    - `INVALID_REQUEST`(400) / gameId 형식이 올바르지 않은 경우
    - `QUEUE_ENTRY_NOT_FOUND`(404) / DB · Redis 등록이 완료되지 않았거나 현재 대기열에 등록하지 않은 경우
- 프론트 계약:
    - `admitted=true`이면 입장이 허용된 상태이다.
    - Queue-Token은 SSE의 `admit` 이벤트에서 확인한다.
    - Kafka Consumer 등록 직후까지는 일시적으로 `QUEUE_ENTRY_NOT_FOUND`(404)가 발생할 수 있다.

### 3.3 대기 순번 실시간 스트림 (SSE)

- `GET /api/v1/queues/{gameId}/stream`
- 인증: JWT
- 설명:
    - 연결 후 약 3초 뒤 처음 대기 상태를 확인한다.
    - 이후 약 3초마다 현재 대기 상태를 조회해 `rank` 이벤트를 전송한다.
    - SSE 연결 유지 시간은 최대 60초 이다.
    - 입장 허용 상태가 확인되면 `rank` 이벤트 다음에 `admit` 이벤트를 전송한다.
    - `admit` 이벤트 전송 후 SSE 연결을 정상 종료한다.
    - Kafka Consumer 등록 전이라 대기 이력이 없으면 연결을 종료하지 않고 다음 주기에 다시 확인한다.
    - 마지막 SSE 연결이 종료되면 서버가 **150초**의 재연결 유예시간을 관리한다.
    - 동일 사용자·경기의 SSE 연결 수를 관리하며, 다른 연결이 남아 있으면 대기열 이탈을 처리하지 않는다.
    - 입장 허용으로 정상 종료된 연결은 재연결 유예 대상에서 제외합니다.
    - 마지막 연결 종료 후 유예시간 **150초** 안에 재연결하면 예약된 대기열 이탈을 취소하고 기존 대기 상태를 유지한다.
    - **150초**의 유예시간 동안 재연결되지 않으면 대기 이력을 `CANCELED`로 변경하고 Redis 대기열에서 제거한다.
    - 유예시간 만료 시 `ACTIVE` Queue-Token이 있으면 함께 `REVOKED`로 변경한다.
    - SSE 연결 정보와 **150초**의 재연결 유예시간은 서버 메모리에서 관리한다.
    - 따라서 현재 기능은 백엔드 서버를 1대만 실행하는 환경을 기준으로 동작한다.
- 요청 헤더: `Accept: text/event-stream`
- 응답 헤더: `Content-Type: text/event-stream`
- `rank` 이벤트(대기 중):
    
    ```json
    event: rank
    data: {
    		"rank": 10,
    		"estimatedWaitSeconds": 3,
    		"queueStatus": "WAITING",
    		"admitted": false
    }
    ```
    
- `rank` 이벤트(입장 허용):
    
    ```json
    event: rank
    data: {
    		"rank": 0, 
    		"estimatedWaitSeconds": 0, 
    		"queueStatus": "ADMITTED", 
    		"admitted": true
    }
    ```
    
- `admit` 이벤트:
    
    ```json
    event: admit
    data: {
    		"admitted": true,
    		"queueToken": "qt_{uuid}",
    		"tokenExpiresAt": "2026-07-21T21:50:00"
    }
    ```
    
- 이벤트 응답 시 주의사항:
    - SSE 이벤트 데이터는 공통 ApiResponse 래퍼를 사용하지 않는다.
    - Queue-Token은 발급 시간부터 **21분** 동안 유효한다.
    - 이미 입장이 허용된 사용자가 SSE에 다시 연결해도 활성 토큰이 남아 있으면 `admit` 이벤트를 받을 수 있다.
- 에러:
    - `UNAUTHORIZED`(401) / SSE 연결 요청의 JWT가 없거나 유효하지 않은 경우
    - SSE 연결 이후 상태 조회 또는 이벤트 전송 중 복구할 수 없는 오류가 발생할 시 연결 종료
- 프론트 계약:
    - `rank` 이벤트를 받을 때마다 화면의 대기 순번을 갱신한다.
    - `admit` 이벤트에서 Queue-Token과 만료 시간을 저장한다.
    - `admit` 이벤트 수신 후 좌석 화면으로 이동한다.
    - 이후 Queue-Token이 필요한 API 요청에 Queue-Token 헤더를 포함한다.
    - 시간 만료로 SSE가 종료됐지만 아직 입장하지 못했다면 마지막 연결 종료 후 **150초**의 유예시간 안에 다시 연결한다.
    - 재연결은 클라이언트가 수행하고 서버는 유예시간 동안 기존 대기 상태를 유지한다.
    - 유예시간 **150초**를 초과하면 기존 대기 상태가 취소되므로 다시 대기열 진입을 요청한다.

### **3.4 대기열 취소**

- `DELETE /api/v1/queues/{gameId}/me`
- 인증: JWT
- 설명:
    - 현재 `WAITING` 상태이거나 활성 Queue-Token이 있는 `ADMITTED` 상태인 사용자의 대기열 진입을 취소한다.
    - `ADMITTED` 상태는 함께 취소할 `ACTIVE` Queue-Token이 있는 경우에만 취소한다.
    - 활성 Queue-Token이 있으면 대기 이력을 `CANCELED`로 변경하면서 토큰을 `REVOKED`로 변경한다.
    - DB 상태를 먼저 `CANCELED`로 변경한다.
    - DB 트랜잭션 커밋이 성공한 경우에만 Redis 대기열에서 사용자를 제거한다.
    - DB 트랜잭션이 롤백되면 Redis 대기열 정보는 유지된다.
- 대응 테이블
    - `queue_entry_histories`(status→CANCELED)
    - `Redis ZSet`
    - `admission_tokens`(status→REVOKED)
- 응답(200):
    
    ```json
    { 
      	"success": true,
      	"errorCode": null,
      	"message": "대기열 취소",
        "data": {
    		    "gameId": 10,
    		    "queueStatus": "CANCELED"
        }
    }
    ```
    
- 에러:
    - `UNAUTHORIZED`(401) / JWT가 없거나 유효하지 않은 경우
    - `INVALID_REQUEST`(400) / gameId 형식이 올바르지 않은 경우
    - `QUEUE_ENTRY_NOT_FOUND`(404) / 취소할 대기열 진입 이력이 없는 경우
    - `QUEUE_INVALID_STATUS`(409) / 대기열 상태가 `WAITING` 또는 취소 가능한 `ADMITTED`가 아닌 경우
    - `USER_NOT_FOUND`(404) / 사용자를 찾을 수 없는 경우
- 동시성/멱등 보장:
    - 사용자, 대기 이력과 활성 Queue-Token을 순서대로 잠근 후 상태를 변경한다.
    - 입장 허용, 토큰 소비와 대기 취소가 동시에 처리될 때 상태 충돌을 방지한다.
- 프론트 계약:
    - Kafka Consumer 등록 전에 즉시 취소하면 `QUEUE_ENTRY_NOT_FOUND`(404)가 발생할 수 있다.
    - 취소 성공 후에는 대기열 화면을 종료한다.

---

## **4. 좌석 (game-seats)**

### **4.1 좌석/상태 조회**

- `GET /api/v1/games/{gameId}/seats`
- 인증: JWT + Queue-Token
- 대응 테이블: `game_seats` (+ `seats`, `seat_zones` 조인)
- 설명:
    - 대기열을 통과한 사용자가 좌석 배치도 진입 시 호출.
    - 경기 좌석 재고(`game_seats`) 기준으로 구역(zone), 등급(grade), 물리 좌석(block/row/number), 가격, 판매 상태를 반환한다.
- 요청 파라미터: `gameId`(path), `zoneId`(option), `grade`(option), `status`(option: AVAILABLE/HELD/SOLD/BLOCKED)
- 요청 헤더: `Queue-Token: {입장 토큰}`
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "좌석 현황 조회",
        "data": {
            "gameId": 10,
            "seats": [
                {
                    "gameSeatId": 5001,
                    "seatId": 88001,
                    "zone": {
                        "zoneId": 30,
                        "name": "1루 블루석",
                        "grade": "BLUE"
                    },
                    "block": "A",
                    "row": "3",
                    "seatNumber": "12",
                    "price": 18000,
                    "status": "AVAILABLE"
                }
            ]
        }
    }
    ```
    
- 에러:
    - `QUEUE_TOKEN_REQUIRED`(403)
    - `UNAUTHORIZED`(401)
    - `QUEUE_TOKEN_REVOKED`(410) / 대기 취소 또는 재연결 유예시간 만료로 취소된 Queue-Token인 경우
- 비고:
    - 응답의 좌석 식별자 `gameSeatId`(= `game_seats.id`), 이후 선점 API의 `gameSeatIds`로 사용한다.
    - 좌석 전체를 배치도 단위로 반환하는 non-paged 조회이므로 `PageResponse` 대상이 아니다(`data.seats` 배열 유지).
    - 환불 완료(`REFUNDED`)된 좌석은 `AVAILABLE`로 다시 노출된다. 환불 진행 중·실패 좌석은 `SOLD`로 유지된다.

### 4.2 구역(좌석 등급) 목록 조회

- `GET /api/v1/games/{gameId}/zones`
- 인증: JWT
- 대응 테이블: `seat_zones` (+ `game_seats` 집계)
- 설명: 좌석 배치도 상단의 구역/등급 요약(잔여 좌석 수 포함)을 반환한다.
- 응답(200)
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "구역 요약 조회",
        "data": {
            "zones": [
                {
                    "zoneId": 30,
                    "name": "1루 블루석",
                    "grade": "BLUE",
                    "price": 18000,
                    "availableCount": 124,
                    "totalCount": 300
                }
            ]
        }
    }
    ```
    
- 비고: 구역 전체를 반환하는 non-paged 조회이므로 `PageResponse` 대상이 아니다(`data.zones` 배열 유지).

---

## **5. 예약/선점 (reservations)**

> `reservations`, `reservation_seats`, `game_seats`
> 

### **5.1 좌석 선점 (HOLD) — 동시성 핵심**

- `POST /api/v1/reservations`
- 인증: JWT + Queue-Token + 본인인증 완료
- 대응 테이블: `reservations`, `reservation_seats`, `game_seats`
- 사전 검증 순서
    1. 예매 가능 여부: `games.booking_status = OPEN` 아니면 `BOOKING_NOT_OPEN`(409)
    2. 본인인증: `users.is_verified = false` 이면 `USER_NOT_VERIFIED`(403)
    3. 입장 토큰: `Queue-Token` 검증
    4. 수량 제한 — 누적 보유 좌석 수 기준
        - 집계식 = ① + ②
            - ① `reservations.status = HOLDING`인 예약에 속한 `reservation_seats` 행 수
            - ② `tickets.status`가 `ISSUED`·`REFUND_PENDING`·`REFUND_FAILED`인 티켓 수
        - 집계값 + 요청 좌석 수 > 2 → `MAX_SEAT_COUNT_EXCEEDED`(400)
        - `CONFIRMED` 예약은 ②로 계산되므로 ①에서 제외한다(이중 계산 방지).
        - `REFUND_PENDING`·`REFUND_FAILED`는 좌석이 `SOLD`로 남아 있어 실질 보유이므로 집계에 포함한다. 제외하면 환불 실패 구간에 3좌석 보유가 가능해진다.
    5. 좌석 상태·소속 검증 → 락 획득 → `HELD` 전이
- 설명:
    - 각 `gameSeatId`에 분산락(Redisson) 또는 DB 락(낙관적 `version`/비관적 `FOR UPDATE`) 을 적용해 최초 요청자만 HELD로 전이시키고 TTL을 부여한다.
    - 이후 동일 좌석 요청은 `SEAT_ALREADY_HELD`로 차단한다.
    - 선점 TTL은 10분이며 `reservations.hold_expires_at` / `game_seats.hold_expires_at`에 저장한다.
    - Queue-Token은 검증만 하고 소비하지 않는다. 소비는 티켓 발급 시점이다.
- 요청 헤더: `Queue-Token: {입장 토큰}`
- 요청 바디:
    
    ```json
    {
        "gameId": 10,
        "gameSeatIds": [
            5001,
            5002
        ]
    }
    ```
    
- 응답(201):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "좌석 선점 완료",
        "data": {
            "reservationId": 1001,
            "reservationNo": "RSV-20260711-000001",
            "status": "HOLDING",
            "gameSeats": [
                {
                    "gameSeatId": 5001,
                    "status": "HELD",
                    "price": 18000
                },
                {
                    "gameSeatId": 5002,
                    "status": "HELD",
                    "price": 18000
                }
            ],
            "holdExpiresAt": "2026-07-11 14:30:00",
            "gameAt": "2026-07-11 18:30:00"
        }
    }
    ```
    
    - `holdExpiresAt`는 선점 성공 시각 +10분이다. 주문 생성 시 연장될 수 있으므로 프론트는 #6.1 응답의 값으로 갱신한다.
- 에러:
    - `MAX_SEAT_COUNT_EXCEEDED`(400, 1인 2매 초과)
    - `GAME_SEAT_NOT_IN_GAME`(400, 요청 좌석이 해당 경기 소속 아님)
    - `USER_NOT_VERIFIED`(403, 본인인증 미완료)
    - `QUEUE_TOKEN_REQUIRED`(403) / `QUEUE_TOKEN_INVALID`(403)
    - `BOOKING_NOT_OPEN`(409, 예매 오픈 상태 아님)
    - `SEAT_ALREADY_HELD`(409) / `SEAT_ALREADY_SOLD`(409) / `SEAT_BLOCKED`(409)
    - `LOCK_FAILED`(409) / `QUEUE_TOKEN_ALREADY_USED`(409)
    - `QUEUE_TOKEN_EXPIRED`(410)
    - `QUEUE_TOKEN_REVOKED`(410) / 대기 취소 또는 재연결 유예시간 만료로 취소된 Queue-Token인 경우
    - `HOLD_EXTENSION_LIMIT_EXCEEDED`(409, 재선점 시 HOLD 상한 초과)
- 동시성 규칙:
    - 동일 `gameSeatId`에 동시 100요청 × 100회 반복 시 매회 1건만 201, 나머지는 409(`SEAT_ALREADY_HELD`).
    - over-booking 0건 보장(`uk_game_seats_game_seat` + 락)
    - 서로 다른 좌석 요청은 병렬 처리되어야 한다(글로벌 락으로 직렬화되면 설계 실패)
    - 다좌석 요청 시 `gameSeatId` 오름차순 정렬 후 락을 획득한다(획득 순서 불일치로 인한 데드락 방지). → B3
- 선점 만료 경합 주의:
    - `hold_expires_at`은 주문 생성 트랜잭션이 UPDATE하고, 만료 스케줄러가 READ 후 `HELD→AVAILABLE`로 전이한다. 두 경로가 같은 행을 경합한다.
    - 스케줄러는 `WHERE status='HELD' AND hold_expires_at < now()` 조건부 원자적 UPDATE로 수행하고, 주문 생성은 예약 행을 비관적 락으로 잡은 뒤 연장한다.
    - → 관련 버그: B5(좀비 HOLD), B4(Redis–DB 정합성)
- 관련 버그: B1, B2, B3, B4, B5

### **5.2 선점 남은 시간 조회**

- `GET /api/v1/reservations/{reservationId}/hold-time`
- 인증: JWT
- 대응 테이블: `reservations`(hold_expires_at)
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "선점 잔여 시간 조회",
        "data": {
            "reservationId": 1001,
            "remainingSeconds": 230,
            "status": "HOLDING"
        }
    }
    ```
    
- 에러: `RESERVATION_ACCESS_DENIED`(403) / `RESERVATION_NOT_FOUND`(404)

### **5.3 선점 해제/취소**

- `DELETE /api/v1/reservations/{reservationId}`
- 인증: JWT
- 대응 테이블: `reservations`(status→CANCELED), `game_seats`(status→AVAILABLE)
- 설명: 사용자가 좌석을 취소하면 예약을 `CANCELED`로 바꾸고, 묶인 `game_seats`를 즉시 `AVAILABLE`로 되돌린다.
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "선점 해제",
        "data": {
            "reservationId": 1001,
            "status": "CANCELED"
        }
    }
    ```
    
- 에러:
    - `PRE_RESERVATION_EXPIRED`(410)
    - `RESERVATION_ACCESS_DENIED`(403, 타인 예약)
    - `PRE_RESERVATION_EXPIRED`(410, 만료된 예약의 해제 요청)

---

## **6. 주문 (orders)**

> `orders`, `order_items`
> 

### **6.1 주문 생성**

- `POST /api/v1/orders`
- 인증: JWT
- 대응 테이블: `orders`(status=CREATED, payment_deadline), `order_items`, `reservations`(hold_expires_at 연장), `game_seats`(hold_expires_at 연장)
- 설명:
    - HOLDING 상태 예약을 바탕으로 주문을 생성한다.
    - `reservation_seats`의 가격 합으로 `total_amount`를 확정한다.
    - [결제 기한] 단축하지 않는다.
        
        ```java
        paymentDeadline = 주문 생성 시각 + 8분
        ```
        
    - [선점 연장] 동일 트랜잭션에서 선점 만료를 연장해 불변식(`hold_expires_at ≥ payment_deadline`)을 유지한다.
        
        ```java
        holdExpiresAt = min(
            max(reservations.hold_expires_at, paymentDeadline),
            최초 선점 시각 + 18분
        )
        ```
        
    - 연장 값은 `reservations.hold_expires_at`과 묶인 모든 `game_seats.hold_expires_at`에 동일하게 반영한다. 한쪽만 갱신하면 B4(Redis–DB 정합성 불일치)가 발생한다.
    - 연장 상한 18분은 `선점 10분 + 결제 8분`이 도달할 수 있는 논리적 최대값이며, 좌석 점유가 무한히 늘어나는 것을 막는 가드다.
- 요청 바디:
    
    ```json
    {
    	  "reservationId": 1001,
    	  "discountCode": "WELCOME10",
    	  "deliveryType": "MOBILE"
    }
    ```
    
    - `deliveryType`: `MOBILE`(스마트 티켓) / `PAPER`(지류)
    - 현재 `discountCode`는 반영하지 않는다.
    - `deliveryType`은 `MOBILE` 또는 `PAPER` 값만 검증하며 주문 생성과 응답에는 반영하지 않는다.
- 응답(201):
    
    ```json
    {
    	  "success": true,
    	  "errorCode": null,
    	  "message": "주문 생성",
    	  "data": {
    		    "orderId": 7001,
    		    "orderNo": "ORD-20260711-ABC123",
    		    "totalAmount": 18000,
    		    "status": "CREATED",
    		    "paymentDeadline": "2026-07-11 14:29:00",
    		    "holdExpiresAt": "2026-07-11 14:30:00",
    		    "orderItems": [
    			      {
    				        "orderItemId": 9001,
    				        "gameSeatId": 5001,
    				        "price": 18000
    			      }
    		    ]
    	  }
    }
    ```
    
    - `paymentDeadline`은 항상 주문 생성 +8분이다. `holdExpiresAt`은 연장 결과이며 항상 `paymentDeadline` 이상이다.
    - 예시 1 (연장 없음): 선점 14:20 → 만료 14:30. 주문 14:21 → 결제 기한 14:29. `max(14:30, 14:29) = 14:30`이므로 `holdExpiresAt` 유지.
    - 예시 2 (연장 발생): 선점 14:20 → 만료 14:30. 주문 14:25 → 결제 기한 14:33. `max(14:30, 14:33) = 14:33`이므로 `holdExpiresAt`을 14:33으로 연장(상한 14:38 이내).
    - 예시 3 (상한 적용): 선점 14:20 → 만료 14:30. 주문 14:31 → 이미 만료이므로 `PRE_RESERVATION_EXPIRED`(410). 상한 18분에 실제로 걸리는 경우는 없으며, 상한은 방어적 가드로만 존재한다.
    - 프론트는 `paymentDeadline`을 카운트다운으로 표시한다.
    - `orderNo`가 사용자에게 노출하는 예매번호다. 마이페이지·CS 응대 시 이 번호를 사용한다.
- 에러:
    - `PRE_RESERVATION_EXPIRED`(410)
    - `INVALID_RESERVATION_STATUS`(409, HOLDING 아님)
    - `RESERVATION_ALREADY_ORDERED`(409, 1예약-2주문 시도 → `uk_orders_reservation`)
    - `RESERVATION_ACCESS_DENIED`(403, 타인 예약)
    - `INVALID_REQUEST`(400) / 요청 바디 형식 또는 필수값이 올바르지 않은 경우
    - `UNAUTHORIZED`(401) / JWT가 없거나 유효하지 않은 경우
    - `USER_NOT_FOUND`(404) / 사용자를 찾을 수 없는 경우
    - `RESERVATION_NOT_FOUND`(404) / 예약을 찾을 수 없는 경우
    - `RESERVATION_SEAT_NOT_FOUND`(404) / 예약 좌석을 찾을 수 없는 경우
- 동시성/정합성 보장:
    - 예약 행을 비관적 락으로 획득한 뒤 상태 재검증 → 주문 생성 → 선점 연장을 단일 트랜잭션으로 처리한다.
    - `uk_orders_reservation`이 1예약-1주문을 DB 레벨에서 보장한다.
    - 만료 스케줄러와 경합 시, 락 획득 후 `hold_expires_at < now()`이면 연장하지 않고 410으로 응답한다.

### **6.2 주문 조회**

- `GET /api/v1/orders/{orderId}`
- 인증: JWT
- 대응 테이블: `orders`, `order_items`, `reservations`
- 설명
    - 주문 ID로 본인의 주문 상세 정보를 조회한다.
    - 주문 기본 정보, 결제 기한, 예약의 선점 만료 시간과 주문 좌석 항목을 반환한다.
- 응답(200):
    
    ```json
    {
    	  "success": true,
    	  "errorCode": null,
    	  "message": "주문 조회",
    	  "data": {
    		    "orderId": 7001,
    		    "orderNo": "ORD-20260711-ABC123",
    		    "totalAmount": 18000,
    		    "status": "CREATED",
    		    "paymentDeadline": "2026-07-11 14:29:00",
    		    "holdExpiresAt": "2026-07-11 14:30:00",
    		    "orderItems": [
    			      {
    			        "orderItemId": 9001,
    			        "gameSeatId": 5001,
    			        "price": 18000
    			      }
    		    ]
    	  }
    }
    ```
    
- 에러
    - `INVALID_REQUEST`(400) / orderId 형식이 올바르지 않은 경우
    - `UNAUTHORIZED`(401) / JWT가 없거나 유효하지 않은 경우
    - `FORBIDDEN`(403) / 다른 사용자의 주문을 조회하는 경우
    - `ORDER_NOT_FOUND`(404) / 주문을 찾을 수 없는 경우

### **6.3 주문 취소**

- `POST /api/v1/orders/{orderId}/cancel`
- 인증: JWT
- 대응 테이블:
    - `orders`(status→CANCELED)
    - `reservations`(status→CANCELED)
    - `game_seats`(status→AVAILABLE)
    - `game_seats`(hold_expries_at→null)
- 설명:
    - 취소 가능 상태는 `CREATED` 로 한정한다(`PAID`는 #8.3 티켓 취소로 처리).
    - 주문 취소는 OrderService가 오케스트레이션한다.
        - `order.cancel()` → `reservation.cancel()` → 주문 항목의 각 `gameSeat.available()`
- 응답(200):
    
    ```json
    {
    	  "success": true,
    	  "errorCode": null,
    	  "message": "주문 취소",
    	  "data": {
    		    "orderId": 7001,
    		    "status": "CANCELED"
    	  }
    }
    ```
    
- 에러:
    - `FORBIDDEN`(403, 타인 주문)
    - `ORDER_NOT_FOUND`(404)
    - `INVALID_ORDER_STATUS`(409, CREATED 아님)
    - `INVALID_REQUEST`(400) / orderId 형식이 올바르지 않은 경우
    - `UNAUTHORIZED`(401) / JWT가 없거나 유효하지 않은 경우

---

## **7. 결제 (payments)**

> `payments`, `tickets`
> 

### 7.0 결제 상태값 정의

- `Payment.status`:
    - `READY`: 결제 요청 생성 후 승인 대기
    - `APPROVED`: PG 승인과 로컬 반영 완료
    - `FAILED`: 결제 인증 실패, 기한 만료 또는 승인 상태 불명확으로 종결
    - `PARTIALLY_CANCELED`: 승인 금액 중 일부가 환불됨
    - `CANCELED`: 승인 금액 전체가 티켓 단위 환불로 취소됨
- `PaymentCancel.status`:
    - `PENDING`: PG 취소 결과가 확정되지 않은 취소 이력
    - `DONE`: Toss 부분 취소와 로컬 반영 완료
    - `FAILED`: Toss가 요청을 거절했거나 자동 재시도를 모두 소진한 취소 이력
- `PaymentRecoveryTask.status`:
    - `PENDING`: 최초 실행 대기
    - `PROCESSING`: 스케줄러가 작업을 획득해 처리 중
    - `RETRY`: 다음 자동 재시도 시각까지 대기
    - `COMPLETED`: 복구 작업 완료
    - `FAILED`: 자동 재시도 한도 소진 또는 처리 불가
- `method`:
    - 별도 애플리케이션 Enum으로 제한하지 않는다.
    - 승인 전에는 `null`이며, 승인 완료 후 Toss 응답의 실제 결제 수단 문자열을 저장한다.

### 7.1 결제 요청 (READY 생성)

- `POST /api/v1/payments`
- 인증: JWT
- 대응 테이블: `payments`(생성 또는 READY 결제 재사용), `orders`(결제 가능 상태·기한 조회)
- 설명: 결제 가능한 주문을 기준으로 READY 결제를 생성하거나 기존 결제를 반환한다.
- 요청 헤더: `Idempotency-Key: {client_generated_uuid}`
- 요청 바디:
    
    ```json
    {
        "orderId": 7001
    }
    ```
    
    - `orderId`: 결제할 내부 주문 ID, 필수
- 처리 규칙:
    1. 멱등키 누락·공백 여부를 검증한다.
    2. 동일 멱등키로 생성된 결제가 있으면 사용자와 주문이 일치하는지 검증한 뒤 기존 결과를 반환한다.
    3. 주문의 소유자, 결제 가능 상태와 `paymentDeadline`을 검증한다.
    4. 동일 주문의 재사용 가능한 READY 결제가 있으면 새 Payment를 만들지 않고 활성 멱등키를 새 키로 교체한다.
    5. 동일 주문에 이미 승인된 결제가 있으면 기존 승인 결과를 반환한다.
    6. 신규 결제가 필요하면 READY Payment를 생성한다.
    7. 동일 주문의 동시 생성 요청은 주문 ID 단위 분산락과 DB 유니크 제약으로 직렬화한다.
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "결제 요청 처리 완료",
        "data": {
            "paymentId": 5501,
            "paymentNo": "PAY-20260718000933-1a6bbb60",
            "orderId": 7001,
            "amount": 18000,
            "method": null,
            "status": "READY",
            "pgProvider": "TOSS",
            "pgOrderId": "ORD-20260711-ABC123",
            "paymentDeadline": "2026-07-11 14:29:00"
        }
    }
    ```
    
- 에러:
    - `INVALID_REQUEST` (400): 요청 바디 검증 실패
    - `IDEMPOTENCY_KEY_REQUIRED` (400): 멱등키 누락 또는 공백
    - `PAYMENT_ACCESS_DENIED` (403): 다른 사용자의 주문 또는 결제
    - `ORDER_NOT_FOUND` (404): 주문 없음
    - `IDEMPOTENCY_KEY_CONFLICT` (409): 동일 키가 다른 주문의 결제에 사용됨
    - `INVALID_ORDER_STATUS` (409): 결제할 수 없는 주문 상태
    - `LOCK_FAILED` (409): 주문 단위 결제 생성 락 획득 실패
    - `ORDER_EXPIRED` (410): 주문 결제 기한 경과
- 멱등 규칙:
    - 동일 키의 재요청은 기존 결제 결과를 반환한다.
    - 같은 주문에 새로운 키가 전달되면 재사용 가능한 READY Payment의 활성 키를 교체한다.
    - 이전 키로 도착한 승인·실패 요청은 현재 활성 키와 일치하지 않아 거부된다.
    - 멱등키 이력 전체는 저장하지 않으며 현재 활성 결제 시도만 식별한다.

### **7.2 결제 승인**

- `POST /api/v1/payments/{paymentId}/complete`
- 인증: JWT
- 대응 테이블: `payments`(READY→APPROVED 또는 FAILED), `orders`(CREATED→PAID/CANCELED/EXPIRED), `tickets`(발급), `payment_recovery_tasks`(승인 상태 불명확 시 등록)
- 설명: Toss 인증 결과를 검증하고 승인 API를 호출해 결제를 확정한다.
- 요청 헤더: `Idempotency-Key: {현재 활성 결제 키}`
- 요청 바디:
    
    ```json
    {
        "pgPaymentKey": "toss_payment_key_...",
        "pgOrderId": "ORD-20260711-ABC123",
        "amount": 18000
    }
    ```
    
    - `paymentKey`: Toss 인증 완료 후 발급된 결제 키
    - `orderId`: Toss 인증에 사용된 PG 주문번호
    - `amount`: 승인할 결제 금액
- 처리 규칙:
    1. Payment를 비관적 쓰기 락으로 조회하고 소유자를 검증한다.
    2. 요청 멱등키가 현재 Payment의 활성 키와 같은지 검증한다.
    3. 이미 승인된 동일 요청이면 PG를 다시 호출하지 않고 기존 승인 결과를 반환한다.
    4. FAILED·CANCELED 등 이미 종결된 결제는 재승인을 거부한다.
    5. PG 주문번호와 금액이 로컬 Payment와 일치하는지 검증한다.
    6. Toss confirm 호출 전에 주문 결제 기한을 검증한다. 기한이 지났다면 Toss를 호출하지 않고 결제와 주문을 실패 처리한다.
    7. Toss 승인 완료 응답을 받으면 Payment와 주문을 승인 상태로 전이하고 티켓을 발급한다.
    8. confirm 요청 후 응답 시점에 기한이 지났더라도 PG 정합성을 위해 승인 결과를 반영한다.
    9. 승인 API 응답 또는 재조회 결과가 승인 완료 상태가 아니면서 Toss의 명시적 실패 상태라면 결제와 주문을 실패 처리하고 `200 FAILED` 결과를 반환한다.
    10. 승인 요청과 재조회가 모두 실패해 실제 상태를 알 수 없으면 로컬 결제·주문을 실패 처리하고 자동 환불 복구 작업을 등록한다.
    11. 추후 개선 사항: PG 승인은 확인됐지만 주문 확정이나 티켓 발급 등 로컬 반영이 실패한 경우의 복구·보상 흐름을 별도로 연결한다.
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "결제 승인 처리 완료",
        "data": {
            "paymentId": 5501,
            "paymentNo": "PAY-20260711-000001",
            "status": "APPROVED",
            "method": "간편결제",
            "orderId": 7001,
            "orderStatus": "PAID",
            "tickets": [
                {
                    "ticketId": 9050,
                    "ticketNo": "TKT-20260711-000001",
                    "gameId": 5001,
    								"seat": "1루 A-3-12",
                    "status": "ISSUED",
                    "qrToken": "123e4567-e89b-12d3-a456-426614174000",
                    "gameAt": "2026-08-20T18:30:00"
                }
            ]
        }
    }
    ```
    
- 에러
    - `INVALID_REQUEST` (400): 요청 바디 검증 실패
    - `IDEMPOTENCY_KEY_REQUIRED` (400): 멱등키 누락 또는 공백
    - `PAYMENT_CALLBACK_MISMATCH` (400): PG 주문번호 또는 금액 불일치
    - `PAYMENT_ACCESS_DENIED` (403): 다른 사용자의 결제
    - `PAYMENT_NOT_FOUND` (404): 결제 없음
    - `IDEMPOTENCY_KEY_UNAVAILABLE` (409): 현재 활성 키와 요청 키 불일치
    - `PAYMENT_ALREADY_FINALIZED` (409): 승인할 수 없는 종결 상태
    - `INVALID_ORDER_STATUS` (409): 승인할 수 없는 주문 상태
    - `ORDER_EXPIRED` (410): confirm 호출 전 결제 기한 경과
    - `PAYMENT_CONFIRM_STATUS_UNKNOWN` (502): 승인 요청과 재조회 후에도 실제 PG 상태를 확인할 수 없음
    - `PAYMENT_LOCAL_APPLY_FAILED` (502): PG 상태는 승인 요청으로 등록되었으나, 로컬 상태가 변경되지 않음. 자동으로 PG 결제 취소 등록
- 트랜잭션 정책:
    - `ORDER_EXPIRED`는 결제·주문 실패 상태를 저장한 뒤 예외를 반환한다.
    - `PAYMENT_CONFIRM_STATUS_UNKNOWN`은 결제·주문 실패 및 복구 작업을 저장한 뒤 예외를 반환한다.
    - 두 예외는 필요한 상태 변경이 롤백되지 않도록 제한적으로 `noRollbackFor`를 사용한다.
- 멱등 규칙:
    - 이미 승인된 결제에 같은 활성 키로 다시 요청하면 기존 승인 결과를 반환한다.
    - 현재 활성 키와 다른 승인 요청은 처리하지 않는다.
    - 현재 `Idempotency-Key`는 로컬 결제 시도를 검증하는 용도이며 Toss confirm 요청 헤더에는 전달하지 않는다.

### **7.3 결제 실패**

- `POST /api/v1/payments/{paymentId}/fail`
- 인증: JWT
- 대응 테이블: `payments`(READY→FAILED), `orders`(CREATED→CANCELED)
    - 추후 개선 사항: 결제 실패 이후 예약 취소와 좌석 반환이 필요하면 Order 도메인의 상태 전이 흐름을 통해 `reservations`와 `game_seats`까지 전파한다.
- 설명: Toss 위젯 인증 실패 또는 사용자 중단 결과를 READY 결제와 주문에 반영한다. Toss API는 호출하지 않는다.
- 요청 헤더: `Idempotency-Key: {현재 활성 결제 키}`
- 요청 바디:
    
    ```json
    {
    		"code": "PAY_PROCESS_CANCELED",
    	  "message": "사용자가 결제를 취소했습니다.",
    	  "orderId": "ORD-20260711-ABC123"
    }
    ```
    
- 처리 규칙:
    1. Payment를 비관적 쓰기 락으로 조회하고 소유자를 검증한다.
    2. 요청 멱등키와 PG 주문번호를 검증한다.
    3. READY 결제를 FAILED로 변경하고 실패 코드·메시지와 실패 시각을 기록한다.
    4. 주문도 실패 상태로 전이한다.
    5. 결제가 READY가 아니라면 상태를 덮어쓰거나 주문을 다시 전이하지 않고 현재 결제 결과를 반환한다.
    6. 따라서 FAILED 재호출뿐 아니라 APPROVED·PARTIALLY_CANCELED·CANCELED 상태의 요청도 현재 상태 그대로 응답한다.
    7. 백엔드 실패 API로 READY 결제를 실패 처리한 시점부터 해당 결제 시도는 재시도 불가능한 종결 실패로 본다.
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "결제 실패 처리 완료",
        "data": {
            "paymentId": 5501,
            "status": "FAILED"
        }
    }
    ```
    
- 에러:
    - `INVALID_REQUEST` (400): 요청 바디 검증 실패
    - `IDEMPOTENCY_KEY_REQUIRED` (400): 멱등키 누락 또는 공백
    - `PAYMENT_CALLBACK_MISMATCH` (400): PG 주문번호 불일치
    - `PAYMENT_ACCESS_DENIED` (403): 다른 사용자의 결제
    - `PAYMENT_NOT_FOUND` (404): 결제 없음
    - `IDEMPOTENCY_KEY_UNAVAILABLE` (409): 현재 활성 키와 요청 키 불일치

### 7.4 결제 단건 조회

- `GET /api/v1/payments/{paymentId}`
- 인증: JWT
- 대응 테이블: `payments`, `payment_cancels`(취소 이력 요약)
- 설명: 본인의 결제 상세 상태, 처리 시각과 티켓 단위 취소 이력을 조회한다.
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "결제 조회 완료",
        "data": {
            "paymentId": 5501,
            "paymentNo": "PAY-20260718000933-1a6bbb60",
            "orderId": 7001,
            "amount": 18000,
            "method": "간편결제",
            "status": "APPROVED",
            "pgProvider": "TOSS",
            "failReason": null,
            "approvedAt": "2026-07-18 00:12:23",
            "failedAt": null,
            "canceledAmount": 4000,
    		    "remainingAmount": 14000,
            "cancels": [
                {
                    "paymentCancelId": 8801,
                    "ticketId": 9051,
                    "cancelAmount": 18000,
                    "cancelStatus": "DONE",
                    "cancelReason": "USER_REQUEST",
                    "requestedAt": "2026-07-10 15:00:00",
                    "completedAt": "2026-07-10 15:00:02"
                }
            ]
        }
    }
    ```
    
- 취소 금액 계산:
    - `canceledAmount`: DONE 상태인 티켓별 취소 금액의 합계
    - `remainingAmount`: 최초 결제 금액에서 `canceledAmount`를 뺀 금액
    - 각 `cancelAmount`는 취소 대상 티켓의 OrderItem 가격을 사용한다.
    - 취소 이력은 PENDING·DONE·FAILED 상태를 포함할 수 있다.
- 에러:
    - `PAYMENT_ACCESS_DENIED`(403) / 다른 사용자의 결제인 경우
    - `PAYMENT_NOT_FOUND`(404) / 결제를 찾을 수 없는 경우

### 7.5 티켓 단위 결제 취소

> PaymentController에 공개 취소 엔드포인트를 두지 않는다. 
사용자와 관리자의 환불 진입점은 Ticket API이며, Payment는 내부 서비스 호출로 취소 작업을 등록하고 스케줄러가 비동기로 처리한다.
> 
- 공개 진입점:
    - 사용자: `POST /api/v1/tickets/{ticketId}/cancel` (8.3)
    - 관리자: `POST /api/v1/admin/tickets/{ticketId}/cancel` (8.4)
    - 재시도: `POST /api/v1/tickets/{ticketId}/cancel/retry` (9.13)
- 내부 진입 메서드: `PaymentService.requestTicketPaymentCancel(Ticket ticket, String reason)`
- 처리 규칙:
    1. Ticket 도메인이 환불 요청을 시작하고 티켓을 `REFUND_PENDING`으로 전이한다.
    2. Payment 도메인은 티켓과 연결된 결제를 찾아 `PaymentCancel`을 `PENDING`으로 등록한다.
    3. 복구 스케줄러(`PartialCancelRecoveryHandler`)가 작업을 획득해 Toss 부분 취소 API를 호출한다.
    4. Toss 취소 완료가 확인되면 `PaymentCancel`을 `DONE`으로 변경한다.
    5. 일부 금액만 취소되면 Payment는 `PARTIALLY_CANCELED`, 전체 금액이 취소되면 `CANCELED`이 된다.
    6. 성공 시 `ticketService.completeTicketRefund(ticketId)`를 호출한 뒤, 같은 스케줄러가 `OrderService.refundOrder(orderItemId)`를 호출해 `orders`와 `game_seats`를 정리한다.
    7. 재시도 한도를 모두 소진한 경우 `TicketService.failTicketRefund(ticketId)`를 호출한다.
    8. 동일 티켓의 기존 FAILED 취소 이력이 재시도되면 새 이력을 만들지 않고 기존 PaymentCancel을 재사용한다.
- 비동기 응답 원칙:
    - 최초 취소 요청은 PG 취소 완료를 기다리지 않고 환불 접수 상태를 반환한다.
    - 최종 취소 결과는 티켓 또는 결제 조회를 통해 확인한다.
    - 과거 사용자용 결제 전액 취소 API와 PaymentCancel 직접 재시도 API는 공개하지 않는다.

---

## **8. 티켓 (tickets)**

> `tickets`, `payment_cancels`(조회 연계)
> 

### **8.1 내 티켓 목록**

- `GET /api/v1/tickets`
- 인증: JWT
- 대응 테이블: `tickets`(현재 소유자 `user_id` 기준)
- 요청 파라미터
    
    
    | 파라미터 | 타입 | 필수 | 기본값 | 설명 |
    | --- | --- | --- | --- | --- |
    | `status` | Enum | N | - | `ISSUED` / `REFUND_PENDING` / `REFUND_FAILED` / `REFUNDED` / `USED_ENTERED` / `USED_NO_SHOW` |
    | `page` | int | N | `0` | 0-based |
    | `size` | int | N | `20` | - |
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "티켓 조회",
        "data": {
            "content": [
                {
                    "ticketId": 9050,
                    "ticketNo": "TKT-20260711-000001",
                    "gameId": 10,
                    "seat": "1루 블루석 A-3-12",
                    "status": "ISSUED",
                    "qrToken": "qr_...",
                    "gameAt": "2026-07-11 18:30:00",
                    "refundable": true,
    						    "refundDeadline": "2026-07-10 18:30:00"
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
    
    - `refundDeadline`: `gameAt - 24시간`으로 서버가 계산해 내려준다.
    - `refundable`: `status = ISSUED`이고 `now() < refundDeadline`일 때만 `true`.
    - 에러: `INVALID_REQUEST`(400) / `UNAUTHORIZED`(401)

### 8.2 티켓 상세

- `GET /api/v1/tickets/{ticketId}`
- 인증: JWT
- 대응 테이블: `tickets`, `games`, `game_seats`, `payment_cancels`
- 응답(200): 티켓 1건(경기/좌석/상태/QR 토큰)
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "티켓 상세 조회 완료",
        "data": {
            "ticketId": 9050,
            "ticketNo": "TKT-20260711-000001",
            "gameId": 10,
            "gameSeatId": 5001,
            "seat": "1루 블루석 A-3-12",
            "status": "ISSUED",
            "qrToken": "qr_...",
            "gameAt": "2026-07-11 18:30:00"
        }
    }
    ```
    
- 에러: `TICKET_ACCESS_DENIED`(403) / `TICKET_NOT_FOUND`(404)

### **8.3 티켓 취소**

- `POST /api/v1/tickets/{ticketId}/cancel`
- 인증: JWT
- 대응 테이블: `tickets`, `payment_cancels`, `payments`, `game_seats`, `orders`
    - `game_seats`·`orders`·`payments`의 최종 반영은 이 API 호출 시점이 아니라, 결제 취소가 비동기로 확정되는 시점(§7.5 참조)에 별도로 일어난다.
- 설명: 사용자가 보유 티켓 1장을 선택해 취소한다. 1석 예매면 전액 환불, 2석 예매 후 1석만 취소하면 부분 환불로 처리된다.
    - 취소 단위는 좌석이므로 식별자는 `ticketId`다. 예매번호(`orderNo`)는 취소 실행 키가 아니다.
    - 취소 가능 기한: 경기 시작 24시간 전까지.
        - 경과 시 `TICKET_CANCEL_DEADLINE_PASSED`(409).
    - `ISSUED` 상태만 취소 요청이 가능하다.
    - `reservations.status`는 `CONFIRMED`로 유지한다. 예약은 "그때 확정된 거래"의 기록이다.
    - 중복 요청은 PG를 호출하지 않고 DB 상태만으로 차단한다. `REFUND_PENDING`·`REFUNDED`·`REFUND_FAILED` 각각 전용 에러코드로 응답한다.
- 오케스트레이션:
    - TicketService가 진입점이자 검증 주체다. 접수(동기)와 확정(비동기)이 서로 다른 컴포넌트로 분리되어 있다.
    - PG 호출과 `payments`·`payment_cancels` 갱신은 `PaymentService`(#7.5)에 위임한다.
    - 서로 상대 도메인의 Entity를 직접 수정하지 않고 도메인 서비스 메서드로만 호출한다.
    
    [순서 강제 규칙] 
    
    ```
    [동기 · 접수] TicketService.cancelTicket(userId, ticketId)
      ├─ [검증] 소유자 / ISSUED / 취소 기한
      ├─ ticket.requestRefund()                      → REFUND_PENDING
      └─ paymentService.requestTicketPaymentCancel() → PaymentCancel PENDING 등록 (7.5)
           (PG 응답을 기다리지 않고 즉시 반환)
    
    [비동기 · 확정] PartialCancelRecoveryHandler (결제 복구 스케줄러)
      └─ PG 부분 취소 결과 확정 후
       성공 → ticketService.completeTicketRefund() → ticket.completeRefund() → REFUNDED
            → orderService.refundOrder(orderItemId) → gameSeat.refund()    → AVAILABLE
                             → order.partiallyCancel() / order.cancel()
       실패 → ticketService.failTicketRefund() → ticket.failRefund()    → REFUND_FAILED
           → 좌석은 SOLD 유지
    ```
    
- `gameSeat.refund()`(좌석 반환)은 `ticket.completeRefund()`(REFUNDED 확정) 이후에만 호출되어야 한다.
- 응답(200, 성공):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "티켓 취소 접수 완료",
        "data": {
            "ticketId": 9051,
            "ticketStatus": "REFUND_PENDING",
            "refundRequestedAt": "2026-07-10 15:00:00"
        }
    }
    ```
    
- 응답(200, 환불 실패):
    
    ```json
    {
        "success": false,
        "errorCode": "PG_CANCEL_FAILED",
        "message": "환불 처리에 실패했습니다. 잠시 후 다시 시도해 주세요."
    }
    ```
    
    - 이때 `tickets.status = REFUND_FAILED`, `payment_cancels.status = FAILED`로 기록되며 재시도는 `POST /api/v1/tickets/{ticketId}/cancel/retry`(#8.4)로 처리한다.
- 에러
    - `TICKET_NOT_FOUND`(404)
    - `TICKET_ACCESS_DENIED`(403, 타인 티켓)
    - `TICKET_CANCEL_DEADLINE_PASSED`(409, 경기 시작 24시간 이내)
    - `TICKET_ALREADY_USED`(409, USED_ENTERED·USED_NO_SHOW)
    - `TICKET_REFUND_IN_PROGRESS`(409, REFUND_PENDING)
    - `TICKET_ALREADY_REFUNDED`(409, REFUNDED)
    - `TICKET_REFUND_FAILED_RETRY_REQUIRED`(409, REFUND_FAILED)
        - `POST /api/v1/tickets/{ticketId}/cancel/retry`(8.4) 재시도 안내 문구
    - `PG_CANCEL_FAILED`(502)
- 프론트 계약:
    - 다좌석 주문에서 티켓을 개별 선택해 호출한다. 2장 동시 취소는 2회 호출한다.
    - `REFUND_FAILED` 상태의 티켓은 목록에서 재시도 버튼을 노출한다.

### 8.4 티켓 취소 재시도

- Method / URI: `POST /api/v1/tickets/{ticketId}/cancel/retry`
- 인증: JWT
- 대응 테이블: `tickets`(REFUND_FAILED→REFUND_PENDING), `payment_cancels`(FAILED→PENDING, 기존 행 재사용)
- 설명:
    - `REFUND_FAILED` 상태의 티켓 취소를 재시도(재접수)한다.
    - 새 `payment_cancels` 행을 만들지 않고 같은 티켓의 환불 요청을 다시 `REFUND_PENDING`으로 되돌린 뒤 `PaymentService.requestTicketPaymentCancel()`을 다시 호출한다.
    - `REFUND_FAILED`가 아닌 티켓에 호출하면 `INVALID_STATE_TRANSITION`(409)이 발생한다.
    - 성공/실패 확정은 §8.3과 동일하게 결제 복구 스케줄러가 비동기로 반영한다.
- 응답(200):
    
    ```json
        {
            "success": true,
            "errorCode": null,
            "message": "티켓 취소 재시도 요청 접수 완료",
            "data": {
                "ticketId": 9051,
                "ticketStatus": "REFUND_PENDING",
                "refundRequestedAt": "2026-07-10 15:10:00"
            }
        }
    ```
    
- 에러:
    - `TICKET_NOT_FOUND`(404) / 티켓 없음
    - `TICKET_ACCESS_DENIED`(403) / 타인 티켓
    - `INVALID_STATE_TRANSITION`(409) / `REFUND_FAILED` 상태가 아닌 티켓에 대한 재시도 요청
    - `PG_CANCEL_FAILED`(502)
- 프론트 계약:
    - 마이페이지·티켓 목록에서 `REFUND_FAILED` 상태 티켓에 재시도 버튼을 노출하고 이 API를 호출한다.
    - 응답은 8.3의 접수 응답과 동일한 형태이며, 최종 결과는 티켓/결제 조회로 재확인해야 한다.
- 동시성/멱등 보장:
    - 기존 `payment_cancels` 행을 재사용하며 `attempt_count`를 증가시킨다. 새 취소 이력을 중복 생성하지 않는다.

### 8.5 입장 검증

- `POST /api/v1/tickets/verify`
- 인증: JWT + ADMIN(현장 스태프)
- 대응 테이블: `tickets`(ISSUED→USED_ENTERED, `used_at` 기록)
- 설명: 현장 입장 시 QR 토큰을 스캔해 티켓 유효성을 검증하고 사용 완료 처리한다.
- 요청 바디:
    
    ```json
    {
        "qrToken": "qr_...",
        "gameId": 10
    }
    ```
    
- 응답(200):
    
    ```json
    {
        "success": true,
        "errorCode": null,
        "message": "입장 검증 완료",
        "data": {
            "ticketId": 9050,
            "status": "USED_ENTERED",
            "usedAt": "2026-07-11 17:40:00",
            "seat": "1루 블루석 A-3-12",
            "holderName": "홍*동"
        }
    }
    ```
    
- 에러:
    - `TICKET_NOT_FOUND`(404, QR 토큰 무효)
    - `TICKET_ALREADY_USED`(409, 재입장 시도 — 암표 방지 핵심)
    - `TICKET_REFUND_IN_PROGRESS`(409, 환불 진행 중)
    - `TICKET_ALREADY_REFUNDED`(409, 환불 완료된 티켓)
    - `TICKET_REFUND_FAILED_RETRY_REQUIRED`(409, 환불 실패 상태)
    - `INVALID_REQUEST`(400, 다른 경기 티켓)
- 프론트 계약: 검표 단말에서 QR 스캔 직후 즉시 호출. 재스캔 시 `TICKET_ALREADY_USED`로 "이미 입장 처리됨" 안내와 구분.
- 동시성/멱등 보장: 별도 락 없이 `markEntered()`의 ISSUED 상태 가드로만 방어함. 같은 티켓에 대한 동시 중복 스캔은 극히 드물게 경합할 수 있어 필요 시 후속 개선 여지 있음.

---

## 9. 관리자 (admin)

[9. 관리자 (admin) 페이지 API](https://app.notion.com/p/9-admin-API-d20ac7091a74835887788151d05563c3?pvs=21)

---

## 10. 마이페이지 통합 조회

> 프론트엔드 클라이언트 조합
> 
- Method / URI: 아래 4개를 `frontend-next/app/mypage/page.tsx`가 순서대로/병렬로 호출해 화면 하나로 조합한다.
    
    
    | 순서 | Method / URI | 프론트 훅 | 대응 절 |
    | --- | --- | --- | --- |
    | 1 | `GET /users/me` | `useAuth()` → `getMyProfile()` | §1.6 |
    | 2 | `GET /tickets?page=N&size=100` | `useTickets()` → `getTickets()` (전 페이지 병합·정렬) | §8.1 |
    | 3 | `GET /games` | `useGames()` → `getGames()` | §2.1 |
    | 4 | `POST /tickets/{ticketId}/cancel` | `useCancelTicket()` → `cancelTicket()` | §8.3 |
    | 5 | `POST /tickets/{ticketId}/cancel/retry` | `useRetryCancelTicket()` → `retryCancelTicket()` | §8.4 |
- 인증: JWT (5개 호출 모두 동일 토큰 사용)
- 대응 테이블(필요 시): `users`, `tickets`, `games` (조회만. 쓰기는 4·5번 API가 각자 처리하며 이 화면이 별도로 쓰기 로직을 갖지 않는다)
- 설명:
    - 서버에 마이페이지 전용 통합 엔드포인트가 없어서, 이미 있는 API 5개를 화면(프론트)에서 조합해 동일한 목적을 대체하고 있다.
    - `GET /games`가 필요한 이유: `TicketListResponse`(§8.1 응답)엔 `gameId`만 있고 경기 제목·구장명이 없어서, 전체 경기 목록을 따로 받아 `gameId` 기준으로 클라이언트에서 조인한다.
    - 취소/재시도(4, 5번)는 이 화면 안에서 그대로 §8.3·§8.4 스펙을 재사용한다 — 별도 로직 없음.
- 요청 파라미터: 없음(화면 진입 시 자동으로 1~3번을 호출, 필요한 시점에만 4·5번 호출)
- 응답 (조합 결과, 서버 스키마 아님 — 프론트 내부 상태):
    
    ```tsx
    {
    	  profile: UserProfile,          // 1번 응답
    	  tickets: TicketSummary[],      // 2번 응답(전 페이지 병합)
    	  games: Game[],                 // 3번 응답(1·2 조인용)
    }
    ```
    
- 에러: 각 API의 에러를 그대로 화면에 노출한다.
- 프론트 계약 (해당 시):
    - 티켓 목록은 `getTicketPage(0)`으로 첫 페이지를 받은 뒤 `totalPages`만큼 나머지 페이지를 추가로 호출해 하나의 배열로 합친다.
    - 합친 뒤 `gameAt` 내림차순 → `ticketId` 내림차순으로 정렬해 최신 경기 티켓부터 보여준다.
    - 재시도 가능 여부(`retryable`)는 서버 값이 아니라 `TicketStatus === REFUND_FAILED`로 프론트가 자체 판단한다. `payment_cancels.attempt_count`가 어떤 응답에도 없어서, 재시도 한도 소진 여부까지는 프론트가 정확히 알 수 없다.

---

## 11. 구장 혼잡도 (citydata)

### 11.1 구장 실시간 혼잡도 조회

- Method / URI: `GET /api/v1/congestion/stadiums/{stadiumNum}`
- 인증: 불필요 (공개 API / permitAll)
- 대응 테이블(필요 시): 별도 DB 테이블 없음
- 설명:
    - 특정 구장 번호를 기반으로 서울시 도시데이터 OpenAPI를 연동하여 구장 주변의 실시간 인구 혼잡도, 예상 인구수 범위, 위치 좌표 및 관측 시각 정보를 조회한다.
    - 외부 API 부하 감소 및 응답 속도 최적화를 위해 조회 결과는 Redis에 10분간 캐싱한다.
    - 현재는 잠실야구장의 혼잡도 정보를 기본 제공 중이다.
- 요청 파라미터:
    
    
    | 파라미터 | 위치 | 타입 | 필수 | 설명 |
    | --- | --- | --- | --- | --- |
    | `stadiumNum` | Path | Long | Y | 구장번호(ID). 예: 1(서울종합운동장 야구장) |
- 응답 (200 OK):
    
    ```json
    {
    		  "success": true,
    		  "message": "구장 실시간 혼잡도 조회 성공",
    		  "data": {
    		    "stadiumNum": 1,
    		    "stadiumName": "서울종합운동장 야구장",
    		    "areaName": "잠실종합운동장",
    		    "congestionLevel": "붐빔",
    		    "congestionMessage": "사람들이 몰려있어 혼잡합니다.",
    		    "populationMin": 24000,
    		    "populationMax": 26000,
    		    "latitude": 37.5121,
    		    "longitude": 127.0719,
    		    "observedAt": "2026-09-01 19:30"
    	  }
    }
    ```
    
    - `congestionLevel`: 혼잡도 레벨 (여유 / 보통 / 약간 붐빔 / 붐빔 4단계 문자열).
    - `congestionMessage` : 혼잡도 상세 메시지
    - `populationMin`/`populationMax`: 실시간 예상 인구수 범위(정수).
    - `latitude`/`longitude`: 구장 위경도 좌표(Double)
    - `observedAt`: 데이터 관측 시각(`YYYY-MM-DD HH:mm`).
- 에러:
    - `STADIUM_NOT_FOUND`(404) / 지원하지 않거나 존재하지 않는 구장 ID로 요청한 경우
    - `EXTERNAL_API_ERROR`(502) / 서울시 실시간 도시데이터 API 통신 실패 또는 응답 데이터 이상 시
- 프론트 계약:
    - `congestionLevel` 필드는 배지/태그 컴포넌트 색상 매핑에 사용 가능하다.
    - 위도, 경도 좌표를 활용하여 지도 뷰 핀 표시가 가능하다.
    - 비로그인 사용자 및 예매 전/후 모든 페이지에서 자유롭게 호출할 수 있는 공개 API이다.
- 동시성/멱등 보장 (해당 시):
    - 단순 데이터 조회(GET) 요청으로 멱등성이 보장된다.
    - Redis 캐시(TTL 10분)를 적용하여 외부 API Rate Limit 초과 방지 및 고성능을 보장한다.
    - Redis 장애 시에도 Fallback으로 외부 API를 직접 호출하도록 예외 처리가 되어 있어 가용성을 유지한다.

---

## 부록

### A. 좌석/예약 상태 전이 (ERD 기준)

| 단계 | `game_seats.status` | `reservations.status` | `orders.status` | `payments.status` | `payment_cancels.status` | `tickets.status` | 트리거 API |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 선점 | AVAILABLE→HELD | (생성) HOLDING | - | - | - | - | #5.1 |
| 선점 해제 | HELD→AVAILABLE | HOLDING→CANCELED | - | - | - | - | #5.3 |
| TTL 만료 | HELD→AVAILABLE | HOLDING→EXPIRED | - | - | - | - | (스케줄러) |
| 주문 생성 | HELD (만료시각 연장) | HOLDING (만료시각 연장) | (생성) CREATED | - | - | - | #6.1 |
| 주문 취소 | HELD→AVAILABLE | HOLDING→CANCELED | CREATED→CANCELED | - | - | - | #6.3 |
| 결제 요청 | HELD | HOLDING | CREATED | (생성) READY | - | - | #7.1 |
| 결제 성공 | HELD→SOLD | HOLDING→CONFIRMED | CREATED→PAID | READY→APPROVED | - | (발급) ISSUED | #7.2 |
| 결제 실패 Case1
(수단오류) | HELD 유지 | HOLDING 유지 | CREATED 유지 | READY→FAILED | - | - | #7.3 |
| 결제 실패 Case2 |  |  |  |  |  |  |  |
| (타임아웃/장애) | HELD→AVAILABLE | HOLDING→CANCELED | CREATED→CANCELED | READY→FAILED | - | - | #7.3 |
| 환불 요청 접수 | SOLD 유지 | CONFIRMED 유지 | PAID 유지 | APPROVED 유지 | (생성) PENDING | ISSUED→REFUND_PENDING | #8.3 |
| 환불 성공
(2석 중 1석) | SOLD→AVAILABLE | CONFIRMED 유지 | PAID→PARTIALLY_CANCELED | APPROVED→PARTIALLY_CANCELED | PENDING→DONE | REFUND_PENDING→REFUNDED | #8.3(비동기) |
| 환불 성공
(전액·마지막 1석) | SOLD→AVAILABLE | CONFIRMED 유지 | PARTIALLY_CANCELED→CANCELED
또는 PAID→CANCELED | PARTIALLY_CANCELED→CANCELED
또는 APPROVED→CANCELED | PENDING→DONE | REFUND_PENDING→REFUNDED | #8.3(비동기) |
| 환불 실패 | SOLD 유지 | CONFIRMED 유지 | 유지 | 유지 | PENDING→FAILED | REFUND_PENDING→REFUND_FAILED | #8.3(비동기) |
| 환불 재시도 | - | - | - | - | FAILED→PENDING | REFUND_FAILED→REFUND_PENDING | #8.4 |
| PENDING 복구 | DONE 시 전이
(SOLD→AVAILABLE) | CONFIRMED 유지 | (DONE 시 전이) | (DONE 시 전이) | PENDING→DONE/FAILED | (DONE 시 REFUNDED) | (스케줄러) |
| 입장 검증 | SOLD | CONFIRMED | PAID | APPROVED | - | ISSUED→USED_ENTERED | #8.5 |
| 미입장 자동 처리 | SOLD | CONFIRMED | PAID | APPROVED | - | ISSUED→USED_NO_SHOW | (스케줄러) |

### B. 인증·입장 토큰 요약

| API 그룹 | JWT | Queue-Token | 비고 |
| --- | --- | --- | --- |
| #1 인증, #2 경기 | △/X | X | 회원/공개 |
| #3 대기열 | O | X | 진입·SSE |
| #4 좌석 조회, #5 선점 | O | O | 대기열 우회 차단 |
| #6 주문 | O | X |  |
| #7 결제 | O | X | Queue-Token은 §5.1 선점 시점에만 검증하고, 티켓 발급(§7.2 성공 시)에 소비된다. §7 API 요청 자체에는 Queue-Token 헤더가 필요 없다 |
| #8 티켓 | O | X | 소비 주체는 Ticket 도메인이지만, 소비 시점은 §7.2(결제 승인) 트랜잭션 안에서 티켓이 발급되는 순간이다. §8 API를 클라이언트가 직접 호출하는 시점에 헤더가 필요한 건 아니다 |
| #9 관리자 | O(ADMIN) | X |  |
| #10 마이페이지 | O | X |  |
| #11 구장 혼잡도 | X | X | 완전 공개, 비로그인 허용 |
- 입장 토큰 상태별 응답
    - `ACTIVE` → 정상 통과
    - `USED` → `QUEUE_TOKEN_ALREADY_USED`(409)
    - `EXPIRED` → `QUEUE_TOKEN_EXPIRED`(410)
    - `REVOKED` → `QUEUE_TOKEN_REVOKED`(410)
- §9.25(관리자 전용 좌석 그리드 조회)는 좌석 조회 목적상 §4.1과 로직을 공유하지만, 관리자는 대기열을 거치지 않으므로 Queue-Token을 요구하지 않는다(위 표의 #9 관리자 행과 동일하게 Queue-Token 불필요).

---

end.