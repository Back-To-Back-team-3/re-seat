# Git/GitHub 메뉴얼

## 0. 필수 사항

### 절대 원칙

1. 작업 브랜치는 항상 `develop`에서 만든다 ( `main` 서버).
2. `main`·`develop`에는 직접 push 하지 않는다 → 무조건 PR로만 반영. (현재 브랜치 보호 규칙으로 지정되어 있음)
3. 병합(merge)은 승인이 2명 이상이 되면 아무나 진행한다. 

### 작업 사이클

| 순서 | 내용 |  |
| --- | --- | --- |
| 1 | GitHub Issue 생성 | 작업 지시서: 무엇을 할지 먼저 정의 |
| 2 | `develop` 최신화 → 작업 브랜치 생성 | `git pull origin develop` → `git switch -b feature/…` |
| 3 | 개발 및 커밋(커밋 여러번 해도 괜찮음) | `git add .` → `git commit -m "..."` |
| 4 | 원격 브랜치 push (GitHub에 올리기) | `git push origin feature/…` |
| 5 | Pull Request 생성 (base: `develop`) | GitHub에서 버튼 클릭 (생성시 Draft pull request 선택) |
| 6 | CodeRabbit 리뷰 및 수정, CI 통과 확인 | CodeRabbit이 리뷰하는걸 보고 수정 해야 된다고 판단되면 수정 |
| 7 | Ready for review | 수정과 CI가 통과가 됐다면 Ready for review로 PR Open |
| 8 | 다시 한번 리뷰 및 수정, CI 통과 확인 | 팀원 2명 이상 Approve |
| 9 | `develop` 병합 | 병합 완료시 브랜치 자동 삭제 |
| 10 | 모두 `develop` 최신화 | `git pull origin develop` |

---

## 1. Git 및 협업 규칙

### 1.1 브랜치 전략 (Git Flow)

- 일반 작업 브랜치는 항상 최신 `develop`에서 생성한다.
- 운영 배포 후 긴급 수정이 필요한 경우에만 최신 `main` 에서 `hotfix/*` 브랜치를 생성한다.
- `main` 과 `develop` 에는 직접 push 하지 않고 PR로만 반영한다.
- `main` 과 `develop` 사이의 병합은 커밋 이력을 유지하기 위해 Create a merge commit을 사용한다.

| 브랜치 | 용도 | 규칙 |
| --- | --- | --- |
| `main` | 최종 발표/배포 버전 | 직접 push 금지, `develop`에서만 병합 |
| `develop` | 팀 개발 통합 브랜치 | PR 병합 대상, 직접 push 금지 |
| `feat/*` | 신규 기능 개발 | 새로운 API, 도메인 기능, 화면 흐름 구현 |
| `fix/*` | 버그 수정 | 기존 기능의 오류, 예외, 정합성 문제 수정 |
| `docs/*` | 문서 수정 | README, API 명세, 기획서 수정 |
| `chore/*` | 설정 및 기타 작업 | 빌드 설정, CI/CD, 의존성 관리, 환경 설정, Github 설정 |
| `test/*` | 테스트 추가 | 단위/통합/동시성 테스트 추가 |
| `refactor/*` | 코드 개선 | 기능 변경 없이 구조 개선 |
| `style/*` | 코드 스타일 수정 | 기능 변경 없이 포맷팅, 들여쓰기, 공백, import 정리 |
| `hotfix/*` | 베포 버전 긴급 수정 | `main`에서 분기하여 긴급 오류만 수정하고, `main` 병합 후 `develop`에도 동일 변경 반영 |
- 브랜치명은 아래 형식을 사용

```
<type>/<issue-number>-<domain>-<summary>

예시:
feat/12-queue-sse
fix/35-payment-idempotency
refactor/48-resale-querydsl
docs/53-api-spec
test/61-reservation-lock
chore/4-github-actions-ci
```

| 본문 도메인 | 브랜치/커밋 scope |
| --- | --- |
| User | `user` |
| Team | `team` |
| Stadium | `stadium` |
| Game | `game` |
| SeatInventory | `seat-inventory` |
| Queue | `queue` |
| Reservation | `reservation` |
| Order | `order` |
| Payment | `payment` |
| Ticket | `ticket` |
| Resale | `resale` |
| Transfer | `transfer` |
| 공통 인프라·설정 | `infra` |
| 릴리즈·버전 및 장기 브랜치 동기화 | `release` |
- 작업 흐름

| 순서 | 내용 |
| --- | --- |
| 1 | GitHub Issue 생성 |
| 2 | `develop` 최신화 → 작업 브랜치 생성 |
| 3 | 개발 및 커밋(커밋 여러번 해도 괜찮음) |
| 4 | 원격 브랜치 push (GitHub에 올리기) |
| 5 | Pull Request 생성 (base: `develop`) |
| 6 | 리뷰 및 수정, CI 통과 확인 |
| 7 | `develop` 병합 후 브랜치 삭제 |
| 8 | 모두 `develop` 최신화 |

### 1.2 커밋 메시지 규칙 (Conventional Commits)

- 커밋 메시지는 아래 형식을 사용

```
<type>(<domain>): <subject>

예시:
feat(queue): SSE 대기열 순번 조회 구현
fix(payment): 중복 결제 방지 로직 수정
test(reservation): 좌석 선점 동시성 테스트 추가
docs(notion):API 명세서 수정
```

| 타입 | 의미 |
| --- | --- |
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 |
| `chore` | 설정, 빌드, 기타 작업 |
| `test` | 테스트 코드 |
| `refactor` | 코드 개선 |
| `style` | 포맷팅, import 정리 |
| `hotfix` | 긴급 수정 |
- 커밋 작성 기준
    - 한 커밋에는 하나의 목적만 담는다.
    - 기능 구현, 테스트 추가, 리팩터링은 가능하면 분리한다.
    - 제목은 짧고 명확하게 작성한다.
    - 대기열, 좌석 선점, 결제처럼 검증이 중요한 작업은 테스트 근거를 PR에 남긴다.

### 1.3 Pull Request 규칙

- 일반 작업 PR: base `develop`, compare 작업 브랜치
- 릴리즈 PR: base `main`, compare `develop`
- 긴급 수정 PR: base `main`, compare `hotfix/*`
- hotfix 역반영 PR: base `develop`, compare `main`

```
<type>(<domain>): <subject>

예시
feat(queue): SSE 대기열 순번 조회 구현
fix(reservation): 좌석 선점 만료 처리 수정
```

- PR 본문 템플릿 → `.github/pull_request_template.md`로 저장하면 PR 생성 시 자동 적용된다.

```markdown
## 📌 개요
> 이번 PR에서 구현하거나 수정한 내용을 한두 문장으로 요약
>

여기에 작성

## ✅ 구현/작업 내용
> 주요 구현/작업 내용을 최대한 상세하게 작성
>

- 작업 상세 내용

## 🧪 테스트
> 실행한 테스트 또는 검증 내용을 작성 (필요 없는 항목 삭제)
>

- [ ] 단위 테스트
- [ ] 통합 테스트
- [ ] API 요청/응답 확인
- [ ] 동시성 테스트 또는 부하 테스트
- [ ] 수동 테스트
- [ ] 테스트 미진행

## 🔗 관련 이슈
> 관련 이슈 번호를 작성
>

Closes #이슈번호

## 📷 스크린샷 또는 로그 (선택)
> 로그, 스크린샷, 문서, 논의 내용이 있다면 작성
>

| Before | After |
| --- | --- |
| (이미지 또는 로그) | (이미지 또는 로그) |

## 💬 리뷰어에게
> 리뷰어가 중점적으로 봐야 할 내용이나 고민한 부분 작성
>

여기에 작성

## ✅ 체크리스트

- [ ] Spotless 실행 또는 포맷 확인
- [ ] 커밋 메시지 컨벤션 준수
- [ ] 빌드 및 테스트 통과
- [ ] API/ERD 변경 시 문서 업데이트
- [ ] 예외 상황 확인
```

#### 1.3.1 병합(merge) 정책

| 브랜치 | 리뷰(Approve) | 병합 |
| --- | --- | --- |
| `develop` | 팀원 누구든 2명 이상 승인 | 아무나 |
| `main`  | 팀원 누구든 3명 이상 승인 | 팀장 |
- 병합 전 필수 조건(모두 충족)
    - 리뷰 2명 이상 승인 + CI 통과 + `develop` 기준 충돌 없음 + (API/ERD 변경 시)문서 반영.
    - CI — GitHub Actions 빌드 및 테스트(`./gradlew clean test`) 통과
- 병합 방식:
    - 일반 작업 브랜치 → `develop`: Squash and merge
    - `hotfix/*` → `main`: Squash and merge
    - `develop` → `main`: Create a merge commit
    - `main` → `develop`: Create a merge commit
    - 장기 브랜치 사이에서 Squash and merge를 사용하면 파일 내용은 반영되지만 커밋 이력이 연결되지 않으므로 사용하지 않는다.
- 병합 후 작업 브랜치는 자동 삭제된다.

> GitHub 강제(설정): PR 필수, 승인 2명 이상, CI 통과
> 

### 1.4 이슈 관리 규칙

> Issue(작업 단위 정의): 1 Issue = 1 Branch가 원칙 (작업 단위와 브랜치를 1:1로 맞춰야 추적성 확보됨)
> 
- 이슈 단위: 충분히 작고 독립적인 작업 단위
- 이슈 제목: PR과 동일하게 `<type>(<domain>): <subject>` 형식
    - 예: `chore(infra): GitHub Actions CI 워크플로우 추가`
- 라벨 규칙
    - feat: 기능 구현 이슈
    - docs: 문서 작성 이슈
    - chore: 설정 및 기타 이슈
    - test: 테스트 이슈
    - refactor: 리팩터링 이슈
    - fix: 버그 이슈
    - style: 코드 스타일 이슈
    - hotfix: 긴급 오류 수정 이슈
- 담당자 지정 방식: 이슈 생성 시 해당 도메인 담당자 지정
- 완료 기준: 아래 기준을 모두 통과하였는가?
    - 요건 충족: 최초 등록된 이슈의 해결 조건과 요구 사항이 모두 반영되었나?
    - 검증 및 테스트: 단위 테스트, 통합 테스트
    - 산출물 및 문서화: 필요한 코드 머지, 시스템 설정 변경, 관련 기술 문서 업데이트가 완료되었나?
- 상태 전환: 이슈 관리 도구에서 상태를 완료로 변경
    - GitHub Projects 보드에서 `Todo → In Progress → Review → Done`

#### 1.4.1 표준 이슈 템플릿

- `.github/ISSUE_TEMPLATE/task.md`로 저장하면 이슈 생성 시 자동 적용된다.

```markdown
## 🎯 작업 목적
> 이 작업이 필요한 이유를 작성
>

## ✅ 작업 내용
- [ ] 작업 상세 내용

## 📅 완료 기한
- [ ] 완료 상세 조건

## 📎 참고 사항(선택)
> 관련 문서, 이미지, 논의 내용이 있다면 작성
>

## 👀 이슈 확인
이슈를 확인한 팀원은 체크 표시!
- [ ]
- [ ]
- [ ]
- [ ]
```

---

## 2. 코드 컨벤션

> Naver HackDay Convention Style: [https://naver.github.io/hackday-conventions-java/](https://naver.github.io/hackday-conventions-java/)
> 

### 2.0 Naver HackDay 컨벤션 적용

- Naver Java Formatter 적용
    - Intellij Settings > Editor > Coding Style > Scheme > 톱니바퀴 > Import Scheme > Intellij IDEA code style XML > `re-seat/confing/naver-intellij-formatter.xml` > Apply
    - Intellij Settings > Tools > Actions on Save > [Reformat code], [Optimize imports] 체크
- CheckStyle 적용
    - Intellij Settings > Plugins > [CheckStyle-IDEA] 설치
    - Intellij Settings > Tools > Checkstyle > Configuration File 추가 > Description: Naver Coding Convetion, File: `re-seat/confing/naver-checkstyle-rules.xml` > Next > Value: config/naver-checkstyle-suppressions.xml > Next > Apply

### 2.1 Java 코드 컨벤션

1. 네이밍 규칙
    - 패키지명
        - 소문자 사용, 일반적으로 도메인 역순 구성
        - 예: `com.example.myapp`
    - 클래스/인터페이스명
        - 파스칼 케이스(PascalCase) 사용
        - 명사 형태
        - 예: `public class UserController`
    - 메서드명
        - 카멜 케이스(camelCase) 사용
        - 동사 또는 동사구 형태, 역할을 명확히
        - 예: `getUserById()`, `calculateTotalPrice()`
    - 변수명
        - 카멜 표시법(camelCase) 사용
        - 의미가 명확하도록 작성
        - 예: `int userCount`, `String firstName`
    - 상수명
        - 모든 문자를 대문자로, 단어 간 구분은 언더스코어(`_`) 사용
        - 예: `public static final int MAX_SIZE = 100;`
2. 들여쓰기/공백/중괄호
    - 들여쓰기: 공백 4칸
    - 중괄호 위치
        
        ```java
        public void userName() {
           // ...
        }
        ```
        
    - 띄어쓰기
        - 연산자 주위에 공백: `int sum = a + b;`
        - 키워드 뒤에 공백: `if (condition) {...}`
3. 주석(Comment):
    - Javadoc 주석: 클래스, 메서드, 필드에 대한 설명
        
        ```java
        /**
         * 사용자 정보를 반환합니다.
         * 
         * @param id 사용자 ID
         * @return 사용자 객체
         */
        public User getUserById(Long id) { ... }
        ```
        
    - 라인 주석: 불분명한 로직 또는 주의가 필요한 부분
        
        ```java
        // 사용자 정보를 DB에서 조회
        User user = userRepository.findById(id);
        ```
        
4. 에러 처리: 충분히 의미 있는 예외를 던지고, `try-catch` 시 구체적인 메시지 로그.
5. 컬렉션 타입: 제네릭(Generics)을 사용해 타입 안정성 보장.
6. 코드 길이 제어: 메서드가 너무 길어지지 않도록 분리해서 작성.

### 2.2 Spring / Spring Boot 네이밍 컨벤션

1. DTO 네이밍: 
    - 클라이언트의 입력을 받는 요청 객체는 XxxRequest(예: `SeatReserveRequest`),
    - 서버의 연산 결과를 화면에 반환하는 응답 객체는 XxxResponse(예: `TicketDetailResponse`) 접미사를 붙여 관리한다.
2. Entity 네이밍: 
    - 도메인의 핵심 의미를 담은 단수형 명사(예: `도메인명(Seat, Reservation)`)로만 명명하여 직관성을 높인다.
3. Repository 메서드 네이밍: 
    - Spring Data JPA의 쿼리 메서드 표준 규격을 준수한다.
    - 단건 조회는 findByXxx(예: `findByGameId()`), 존재 여부 검증은 existsByXxx(예: `existsbyReserveKey()`) 구조를 따르며 파라미터 컬럼명은 카멜 케이스로 맞춘다.
4. 예외 클래스 네이밍:
    - 시스템이 정의한 비즈니스 커스텀 예외 클래스는 반드시 최상위 예외 또는 실행 예외를 상속 받으며, 명확한 원인을 인지할 수 있도록  XxxException(예: `InvalidRequestException`) 이라는 접미사로 종결한다.
5. 테스트 메서드 네이밍:
    - 테스트의 가독성과 인과관계를 보장하기 위해 BDD(Behavior-Driven-Development)스타일의 구조적 명명법인  `should_기대결과_when_조건`  형식을 사용한다. 단어 사이는 언더바로 연결하여 스페이스 가독성을 대체한다. (예: `should_throwException_when_seatAlreadyHeld()`)

### 2.3 Spring / Spring Boot 코드 컨벤션

1. 아키텍처 (Layerd Architecture)
    
    ```
    src/main/java/com/example/demo
    ├─ domain
    │  └─ user
    │     ├─ controller        # HTTP 요청/응답 처리 
    │     ├─ dto
    │     ├─ entity            # 엔티티 클래스
    │     ├─ exception
    │     ├─ repository        # 데이터베이스 접근
    │     └─ service           # 비즈니스 로직 처리
    ├─ global
    │  ├─ config               # 설정 클래스
    │  ├─ common
    │  ├─ security
    │  └─ exception
    └─ ReseatApplication.java  # 메인 클래스(컴포넌트 스캔이 하위까지 적용)
    ```
    
2. 클래스 및 어노테이션 규칙
    - Controller 클래스
        - `@RestController` 또는 `@Controller` 사용.
        - 요청 경로: `@RequestMapping`(클래스 레벨), `@GetMapping`, `@PostMapping`(메서드 레벨).
    - Service 클래스
        - `@Service` 사용.
        - 트랜잭션이 필요한 경우 `@Transactional` 부여.
    - Repository 클래스
        - `@Repository` 사용
        - JPA에서는 `CrudRepository`, `JpaRepository` 등을 상속.
        - 데이터 접근 로직 구현.
    - Entity 클래스
        - `@Entity`, `@Table` 사용.
        - 필드는 `@Id`, `@Column`으로 매핑.
3. Bean 주입 방식(Lombok)
    - 생성자 주입 권장.
    - Setter 주입: 선택적인 의존성에만 사용.
4. 프로퍼티/설정 파일 관리
    - `application.yml` 파일에 환경별 설정(profiles) 구분
    - 예: `application-dev.yml`, `application-prod.yml`
    - 빈 설정: `@ConfigurationProperties`를 사용해 명시적으로 관리
5. 자동 설정(Autoconfiguration)과 사용자 설정
    - Spring Boot가 제공하는 Auto Configuration을 최대한 활용한다.
    - 필요시 `@Configuration` 클래스로 추가 설정한다.
6. 로깅(Logging)
    - 로그 레벨(Log Level) 적용 및 판단
        - `WARN` (예외 처리된 비즈니스 거부) : 시스템의 결함이나 장애는 아니지만, 비즈니스 로직 상 거부되거나 경합에서 밀려난 예외 상황 기록
        - `ERROR` (통제 및 추적이 필요한 시스템 장애) : 시스템 장애 등 즉각적인 조치가 필요한 심각한 에러
        - `INFO` (비즈니스 마일스톤 및 흐름 추적) : 서버 시작 / 종료, 외부 API 요청 / 응답, 중요한 비즈니스  트랜잭션 성공 등 핵심 흐름
        - `DEBUG` (개발 및 로컬 검증 전용) : 개발 중에 변수값, 상세한 로직 분기 등을 확인하기 위한 상세 로그
    - 로거 사용 정책
        - 콘솔 출력 제한 : 서비스 성능 저하와 I/O 병목을 유발하는 `System.out.println()` 및 `e.printStackTrace()` 의 사용을 금지

---

## 3. 개발 순서

1. **1단계: 인프라·도메인 골격**
    - `build.gradle`, `application.yml`, 시드 데이터, Docker/CI 골격을 먼저 확정해 push.
    - 각 도메인 Entity·Repository 골격 정의 → 나머지 파트가 이 위에서 작업 가능.
2. **2단계: API 골격 (도메인별)**
    - Controller/Service/DTO 라우팅과 비즈니스 로직.
    - Entity 확정 전에는 라우팅 뼈대·의사코드 준비.
3. **3단계: 핵심 동시성·대기열·결제**
    - 좌석 선점 락(C), 대기열 SSE/입장 토큰(B), 멱등 결제(D).
    - 재현 대상 버그 B1/B2/B4/B6/B7 중심.
4. **4단계: 통합·검증·문서**
    - E2E 통합, 부하/회귀 테스트, 버그 리포트, API/ERD 문서 갱신.
- PR 담자 역할: PR(Pull Request)이 올라오면 팀원 1명 이상 리뷰를 확인한 뒤 병합(merge)한다. merge 후 슬랙/디스코드 등에 공지한다.

### 3.1 패키지 구조

- `global/`(공통)
- `domain/<도메인>/{controller, service, repository, entity, dto, exception}` 구조
- 도메인:
    - user, team, stadium, game, seat_inventory, queue, reservation, order, payment, ticket
    - resale(2차)
    - transfer(2차)

### 3.2 주요 파일·도메인 담당

| 소유 도메인 | 핵심 책임 | 소유 설정 파일 |
| --- | --- | --- |
| A. 인증·사용자·인프라 | 회원/인증(JWT·Security), 소셜 로그인, 본인 인증(PortOne) 연동, 외부 API(카카오맵·서울시 실시간 도시데이터) 연동, 모니터링(Prometheus/Grafana), Docker, GitHub Actions CI/CD | `build.gradle`, `application.yml`, `.github/workflows/*`, 시드 데이터 |
| B. 대기열·주문 | 가상 대기열(Redis ZSet), SSE 순번, admission, 입장 토큰, 주문 생성·결제 기한·취소/만료 | queue·order 도메인 |
| C. 경기·좌석·예약 | 경기 목록 공개 API, 경기별 좌석 조회, 좌석 임시 선점(HOLD)·TTL, 분산 락(Redisson), 락 전략 비교 | game·stadium·seat_inventory·reservation 도메인 |
| D. 결제 | Toss Payments 연동, 멱등 승인, 부분·전체 환불, PG 상태 기반 복구 | payment 도메인 |
| E. 티켓 | 티켓 발급·상태 관리, QR 검표·재발급, 관리자 조회·강제 취소 | ticket 도메인 |
- 공유 설정 파일(`build.gradle`, `application.yml`, CI 워크플로우, 시드 데이터)은 인프라가 소유한다.
- 다른 담당이 바꿔야 하면 인프라 담당자에게 먼저 알린다(충돌 방지).

### 3.3 브랜치 전략

- 작업은 `develop`에서 분기, PR은 `develop`으로.

---

## 4. 초기 세팅

### 4.1 레포지토리 생성 및 환경 설정

#### 4.1.1 Collaborator 초대 — **merge 권한 부여 방법**

모든 팀원이 레포에 push할 수 있도록 권한을 부여한다.

1. GitHub 레포지토리 페이지 → `Settings`
2. 왼쪽 메뉴 `Collaborators` 클릭
3. `Add people` 버튼으로 팀원 GitHub 계정 초대 (팀원 전원)
4. 각 팀원이 이메일로 온 초대를 수락

#### 4.1.2 브랜치 보호 규칙 (`main` + `develop`)

실수로 통합 브랜치에 직접 push하는 것을 막는다.

1. `Settings` → `Branches` → `Add branch ruleset`
2. `main`: 직접 push 금지 + PR 필수
3. `develop`: 직접 push 금지 + PR 필수 + (권장) Require status checks to pass로 CI 필수 지정
4. `Restrict who can merge`는 켜지 않는다. 
    - 켜면 지정된 사람만 병합 가능해져, PR 담당자의 PR을 팀원이 병합할 수 없다.

#### 4.1.3 Spring Boot 프로젝트 초기 파일 생성 후 main에 최초 push

팀원들이 브랜치를 딸 기반이 되는 초기 커밋

1. [start.spring.io](https://start.spring.io/) 접속
2. 아래 의존성 추가하여 프로젝트 생성
    - Spring Web
    - Spring Data JPA
    - MySQL Driver
    - Spring Data Redis (Reactive 아님)
    - Spring Security
    - Validation
    - Lombok
    - (Redisson·JWT(jjwt)·QueryDSL은 initializr에 없으므로 `build.gradle`에 직접 추가)
3. 다운로드 → 압축 해제 → IntelliJ로 열기
4. `main`에 최초 push 후, `develop` 생성 및 기본 브랜치 지정
    
    ```bash
    git init
    git add .
    git commit -m "chore: 프로젝트 초기 세팅"
    git branch -M main
    git remote add origin https://github.com/[레포지토리주소].git
    git push -u origin main
    
    # develop 통합 브랜치 생성
    git switch -c develop
    git push -u origin develop
    # GitHub → Settings → General → Default branch를 develop으로 변경
    ```
    

### **4.2 파트별 로컬에 받아오기**

#### **4.2.1 레포지토리 clone**

```bash
git clone https://github.com/[레포지토리주소].git
cd [프로젝트폴더명]
```

#### **4.2.2 develop에서 자기 작업 브랜치 만들기**

```bash
git switch develop
git pull origin develop

# 예시 (이슈번호-도메인-요약)
git switch -c feat/12-queue-sse            # B: 대기열
git switch -c feat/20-reservation-hold     # C: 좌석 선점
git switch -c feat/30-payment-idempotency  # D: 결제
```

#### **4.2.3 브랜치를 GitHub에 올리기 (최초 1회)**

```bash
git push -u origin feat/12-queue-sse
```

- `-u` 옵션은 최초 1회만 쓴다. 이후부터는 `git push`만 해도 된다.
- 여기까지 하면 준비 완료. 이후부터는 5번의 일반 작업 흐름을 반복한다.

---

## 5. 작업 흐름

### **5.1 작업 시작 전 — 항상 develop 최신 상태로 맞추기**

- 작업을 시작할 때마다 반드시 실행한다. 건너뛰면 나중에 충돌이 생긴다.

```bash
# Develop 최신화 후 작업 진행
git switch develop                           # develop 브랜치 이동
git pull origin develop                      # develop 최신화
git switch -c feat/이슈번호-domain-summary    # 본인 브랜치 생성
```

```bash
# 작업 하던 중 Develop이 최신화 됨
# (commit이 되어 있다면)
git status                                 # 현재 상태 확인
git switch develop                         # develop 브랜치 이동
git pull origin develop                    # develop 최신화
git switch feat/이슈번호-domain-summary     # 본인 브랜치 이동
git merge develop                          # develop 병합
```

```bash
# 작업 하던 중 Develop이 최신화 됨
# (commit을 하지 않은 변경사항이 있음)
git stash push -m "작업내용"                # 하던 작업 임시 저장
git switch develop                         # develop 브랜치 이동
git pull origin develop                    # develop 최신화
git switch feat/이슈번호-domain-summary     # 본인 브랜치 이동
git merge develop                          # develop 병합
git stash pop                              # 하던 작업 복구
```

> 팀원이 새로 merge한 게 없더라도 습관적 실행하는 게 좋다. 나중에 충돌이 한꺼번에 터질 수 있다.
> 

**현재 브랜치가 어디인지 모를 때:**

```bash
git branch    # 브랜치 목록. * 표시가 현재 브랜치.
git status    # 현재 브랜치 + 변경된 파일 목록
```

### **5.2 코드 작업**

평소처럼 IntelliJ에서 코드를 작성한다.

#### **5.2.1 작업 내용 저장 (commit)**

```bash
git add .                                      # 변경한 파일들을 커밋 목록에 담기(스테이징)
git commit -m "feat: SSE 대기열 순번 조회 구현"  # 담은 변경을 이력으로 저장 + 메모
```

**특정 파일만 선택해서 커밋하고 싶을 때:**

```bash
git add src/main/java/.../queue/QueueController.java    # 이 파일만 담기
git commit -m "feat: 대기열 진입 컨트롤러 추가"
```

#### **5.2.2 GitHub에 올리기 (push)**

```bash
git push origin feat/controller   # 내 브랜치의 커밋들을 GitHub에 업로드
```

#### 5.2.3 IDE 종료할 때

- 별도 명령어 없이 그냥 닫으면 된다.
- 브랜치는 사라지지 않고, 다음에 IntelliJ를 다시 열면 마지막 브랜치 그대로 유지된다.

### **5.3 Pull Request(PR) 올리는 법**

#### **5.3.1 작업 단위가 완성됐을 때 — Pull Request 생성**

1. GitHub 사이트에서 레포지토리 페이지 접속
2. 상단에 뜨는 `Compare & pull request` 버튼 클릭 (없으면 `Pull requests` 탭 → `New pull request`)
3. **base: `develop` ← compare: `feature/본인브랜치`** 확인 (base가 `main`이면 잘못된 것)

#### 5.3.2 제목과 설명 작성

**PR 제목 예시:**

```bash
feat(queue): SSE 대기열 순번 조회 구현
```

**PR 설명 예시:**

```markdown
## 🎯 작업 목적
대기열 진입 시 SSE로 실시간 순번을 푸시하는 기능 구현

## ✅ 작업 내용
- [] QueueController 진입/스트림 API
- [] Redis ZSet 순번 조회

## 📅 완료 기한
- [] 2026-07-02(목)

## 📎 참고 사항(선택)
> 관련 문서, 이미지, 논의 내용이 있다면 작성
>

## 👀 이슈 확인
이슈를 확인한 팀원은 체크 표시!
- [ ]
- [ ]
- [ ]
- [ ]
```

- PR 작성할 때 리뷰어에게 봐줬으면 하는 부분을 적으면 좋다.

#### 5.3.3 `Create pull request` 클릭 → 리뷰어 지정, CI 결과 확인

#### 5.3.4  디스코드에 PR 링크 공유

---

## 6. PR 리뷰 & 병합 (PR 담당자)

PR이 올라오면 아래 순서로 처리한다.

> 병합 정책: 리뷰어 1명 이상 승인 + CI 통과 시, 원칙적으로 작성자 본인이 `develop`에 Squash merge하고 브랜치를 삭제한다. `develop → main` 릴리즈 병합은 발표/배포 시점에 인프라 담당이 수행한다.
> 

### **6.1 GitHub에서 코드 확인**

- Pull Request 페이지에서 `Files changed` 탭을 눌러 변경된 코드를 훑어본다.
- CI(Actions) 체크 확인한다.
- 문제 없으면 `Review changes` → `Approve`

### **6.2 merge 실행**

1. 리뷰 승인(Approve) + CI 통과 확인
2. `Squash and merge` → `Confirm`
3. `Delete branch`로 병합된 작업 브랜치 삭제

### **6.3 팀원에게 공지**

```
[브랜치명] merge 완료. 
[feat/12-queue-sse] develop 병합 완료. 각자 pull 받으세요.
```

- 승인 확인 받은 팀원은 `git pull origin develop` 실행하고 자기 브랜치에 pull.

### 6.4 리뷰어

- PR 작성할 때 리뷰어가 봐줫으면 하는 부분을 적으면 좋다.

리뷰어가 보면 좋은 점

- [ ]  의도대로 동작하는가? — 로직이 요구사항을 충종하는가
- [ ]  컨벤션을 지켰는가? — 네이밍, 패키지 위치
- [ ]  예외/엣지 케이스 처리가 있는가? — 동시성/결제에서 중요함
- [ ]  `develop`과 충돌날 만한 변경인가?
- [ ]  이해 안 되는 부분은 무조건 질문한다.

---

## 7. 병합 후 pull — 내 브랜치 최신화

- 누군가 `develop`에 병합했다는 공지를 받으면 즉시 실행한다.

```bash
git switch feat/20-reservation-hold     # 자기 브랜치
git pull origin develop                 # develop 최신 반영
```

---

## **8. 충돌(Conflict) 해결하는 법**

### **8.1 충돌(conflict)이 발생한 경우**

- IntelliJ에서 충돌난 파일을 열면 아래 표시가 보인다.

```
<<<<<<< HEAD
내 브랜치의 코드
=======
develop(팀원)에서 온 코드
>>>>>>> main
```

- 어느 코드를 남길지 직접 선택하고 저장한 뒤 다시 commit한다.
- 해결이 어려우면 PR 담당자에게 알린다.

### 8.2 충돌 해결하기

1. 충돌난 파일을 IntelliJ에서 열면 빨간 줄로 표시된다.
2. `<<<`, `===`, `>>>` 표시를 모두 삭제하고 원하는 코드만 남긴다
3. 두 코드를 합쳐야 할 경우 직접 편집한다.
4. 저장 후 다시 커밋한다.

```bash
git add .
git commit -m "fix: develop 병합 충돌 해결"
```

> IntelliJ의 `Git` 메뉴 → `Resolve Conflicts`를 쓰면 GUI로 쉽게 해결할 수 있다.
> 

### 8.3 충돌 방지 규칙

- `build.gradle`, `application.yml`, `.github/workflows/*`, 시드 데이터는 인프라 담당만 수정한다.
- 엔티티 필드를 추가·변경할 때는 디스코드에 먼저 공유 후 작업한다.
- ERD·API 명세를 바꿀 때도 사전 공유 필수(문서 담당 반영).
- 작업 시작 전 `git pull origin develop`은 무조건 실행한다.

---

## 9. 실수했을 때 되돌리기

### 9.1 파일 수정 취소 (커밋 전)

```bash
git restore 파일명                    # 특정 파일만 되돌리기
git restore .                         # 전체 되돌리기
```

> **주의:** 이 명령어는 되돌릴 수 없다. 신중하게 사용할 것.
> 

### 9.2 git add 취소 (커밋 전)

```bash
git restore --staged 파일명           # 특정 파일 add 취소
git restore --staged .                # 전체 add 취소
```

### 9.3 커밋 메시지를 잘못 썼을 때 (push 전에만 가능)

```bash
git commit --amend -m "fix: 올바른 메시지"
```

> **push한 후에는 사용하지 않기.** 팀원들과 충돌이 생긴다.
> 

### 9.4 마지막 커밋을 통째로 취소하고 싶을 때 (push 전에만 가능)

```bash
git reset HEAD~1    # 커밋만 취소, 파일 변경사항은 유지
```

### 9.5 실수로 main 브랜치에서 작업했을 때

```bash
# 1. 지금까지 작업한 내용을 임시 저장
git stash

# 2. 내 브랜치로 이동
git switch -b feat/12-queue-sse

# 3. 임시 저장한 내용 불러오기
git stash pop
```

> `main`·`develop`은 직접 push가 막혀 있으니, 파일 작업만 했다면 위 방법으로 충분하다.
> 

---

## 10. 자주 쓰는 명령어 모음

### 상태 확인

```bash
git status              # 현재 브랜치, 변경된 파일 목록
git branch              # 브랜치 목록 (* = 현재 위치)
git log --oneline       # 커밋 이력 한 줄로 보기
git diff                # 수정한 내용 상세 보기 (커밋 전)
```

### 브랜치 이동

```bash
git switch develop             # 통합 브랜치로
git switch feat/12-queue-sse
```

### 기본 작업

```bash
git add .                                   # 전체 파일 스테이징
git add 파일명                              # 특정 파일 스테이징
git commit -m "메시지"                      # 커밋
git push origin feat/12-queue-sse          # push
git pull origin main                       # main 최신화
```

### 임시 저장 (stash)

```bash
git stash                     # 현재 작업 임시 저장
git stash pop                 # 임시 저장 불러오기
git stash list                # 임시 저장 목록 확인
```

---

## 추가

**Q. develop에 아무것도 병합 안 됐는데 `git pull origin develop` 해야 하나요?**

- 변경사항이 없으면 "Already up to date." 라고 뜨고 끝난다.
- 습관적으로 해두면 나중에 충돌을 예방할 수 있어서 권장.

**Q. 작업하다가 IDE 끄면 브랜치가 사라지는지?**

- 아니다. 로컬에 저장되어 유지된다. 다음에 열면 마지막 브랜치 그대로다.

**Q. push까지 했는데 실수를 발견했다면?**

- 수정 후 새로 커밋하고 push하면 된다. 커밋은 쌓이는 구조라 덮어쓰기가 된다.

```bash
# 수정 후
git add .
git commit -m "fix: 오타 수정"
git push origin feat/12-queue-sse
```

**Q. 내 브랜치가 어딘지 모르겠다면?**

- `git branch` 입력하면 `*` 표시가 현재 위치이다. IntelliJ 우측 하단에서도 확인할 수 있다.

**Q. PR 올렸는데 base가 `main`으로 되어 있어요.**

- PR 화면 상단에서 base를 `develop`으로 바꾸면 된다. 우리 팀은 항상 `develop`이 base다.

**Q. 리뷰에서 수정 요청을 받았어요. 어떻게 하나요?**

- 같은 브랜치에서 코드를 고치고 `git add` → `git commit` → `git push`만 하면 **PR에 자동 반영**된다(PR을 새로 만들 필요 없음).

---

end.
