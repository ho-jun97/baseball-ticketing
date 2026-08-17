# CLAUDE.md

## 프로젝트 개요

야구 좌석예매 시스템. 핵심 과제는 **동일 좌석에 대한 동시 예매 요청을 안전하게 처리하는 것**이다 — 기능을 추가하거나 리팩터링할 때도 이 목표를 깨뜨리지 않는지 항상 확인한다.

설계 문서:
- ERD: `docs/design/erd.md`
- API 명세: `docs/design/api-spec.md`

## 기술 스택

- Java 21 + Spring Boot 4.1.x + JPA/Hibernate, Gradle(Groovy DSL)
- DB: PostgreSQL (로컬은 `docker-compose.yml`로 실행)
- 인증: JWT (jjwt)
- 테스트: JUnit 5 + AssertJ(`spring-boot-starter-test`), DB 제약/동시성 검증은 Testcontainers(`spring-boot-starter-data-jpa-test`, `spring-boot-testcontainers`)

## 핵심 설계 원칙 (반드시 지킬 것)

1. **물리적 좌석과 경기별 좌석 상태를 분리한다.**
   `seat`(정적: 구역·열·번호)와 `game_seat`(동적: 경기별 판매 상태)는 별개 테이블이다. 동시성 락은 항상 `game_seat` row 단위로 건다(`@Version` 낙관적 락) — "좌석 자체"가 아니라 "이번 경기의 이 좌석 상태"를 잠그는 것이 핵심이다. 좌석 상태를 다루는 코드에서 이 분리를 깨지 않는다.

2. **가격은 좌석이 아니라 구역(`section`) 단위로 고정한다.** 경기별 변동가는 1차 스코프 밖이다.

3. **좌석 중복 배정은 `reservation_seat.game_seat_id` UNIQUE 제약으로 막는다.** 이미 설계에 포함된 제약이므로 애플리케이션 로직만으로 중복 방지를 재구현하려 하지 않는다.

## 동시성 / 상태 정책

- **HOLD TTL = 5분.** 만료된 HOLD는 스케줄러가 주기적으로 스캔해 `game_seat`를 AVAILABLE로, 연결된 예매를 EXPIRED로 되돌린다. **이 스케줄러는 아직 미구현**(설계 문서상 "4주차 구현" 예정) — 이미 존재한다고 가정하지 않는다.
- 상태(enum) 값:
  | 엔티티 | 값 |
  |---|---|
  | `game_seat.status` | AVAILABLE, HOLD, SOLD |
  | `reservation.status` | PENDING, CONFIRMED, CANCELED, EXPIRED |
  | `payment.status` | PENDING, COMPLETED, FAILED, REFUNDED |
  | `game.status` | SCHEDULED, CANCELED, CLOSED |
- 핵심 에러 계약:
  - `POST /api/games/{gameId}/reservations/hold` → 요청 좌석 중 하나 이상이 이미 HOLD/SOLD면 `409 Conflict` (`SEAT_ALREADY_TAKEN`)
  - `POST /api/reservations/{reservationId}/payments` → HOLD TTL 만료 시 `410 Gone` (`HOLD_EXPIRED`)

## API 컨벤션

- base path: `/api`
- 인증: `Authorization: Bearer {JWT}` — 로그인/회원가입, 경기·좌석 조회 GET을 제외한 전 엔드포인트에 필요
- 관리자 엔드포인트(`/api/admin/**`)는 `role: ADMIN` 필요
- 전체 요청/응답 스펙은 코드에 반영하기 전에 `docs/design/api-spec.md`를 기준으로 삼는다(여기서 중복 기술하지 않음)

## 빌드 / 실행 / 테스트

```bash
./gradlew build          # 컴파일 + 테스트
docker compose up -d     # 로컬 PostgreSQL 기동 (최초 1회 / 필요시)
./gradlew bootRun         # 애플리케이션 실행 (localhost:8080)
./gradlew test            # 테스트만 실행
docker compose down       # 로컬 PostgreSQL 종료
```

DB 연결 정보는 환경변수(`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`)로 오버라이드 가능하며, 기본값은 `docker-compose.yml`의 로컬 PostgreSQL 설정과 일치한다(`src/main/resources/application.yml`).

## 테스트 전략

두 계층으로 나뉜다:

1. **순수 단위 테스트** (`member/MemberTest`, `game/GameTest`, `game/GameSeatTest`, `reservation/ReservationTest`, `payment/PaymentTest`) — Spring 컨텍스트·DB 없이 엔티티의 상태 전이 메서드(`hold()/sell()/release()`, `confirm()/cancel()/expire()`, `complete()/fail()/refund()` 등)만 검증한다. Docker 불필요, 항상 빠르게 실행된다.
2. **Testcontainers 기반 DB 검증** (`support/AbstractPersistenceTest`를 상속하는 `game/GameSeatOptimisticLockTest`, `EntityConstraintTest`) — 실제 PostgreSQL에 UNIQUE 제약과 `@Version` 낙관적 락이 걸려 있는지 검증한다. 특히 `GameSeatOptimisticLockTest`는 이 프로젝트의 핵심 과제(동일 좌석 동시 예매 처리)를 엔티티/영속성 계층에서 직접 증명하는 테스트다 — 다른 트랜잭션이 먼저 `version`을 올린 상황을 시뮬레이션하고, 이후 stale한 엔티티로 flush 시 `OptimisticLockException`이 실제로 발생하는지 확인한다. **로컬에 Docker(또는 Colima)가 떠 있어야 실행된다.**

**`AbstractPersistenceTest`는 "싱글턴 컨테이너" 패턴을 쓴다** — `POSTGRES` 컨테이너를 static 초기화 블록에서 한 번만 `start()`하고 절대 `stop()`하지 않는다. `@Testcontainers`/`@Container`/`@ServiceConnection`로 되돌리지 말 것: 그 조합은 static 필드가 상속되어 여러 테스트 클래스가 컨테이너를 공유한다는 사실을 놓치기 쉽고, JUnit이 "첫 번째로 실행된 서브클래스"의 테스트가 끝난 뒤 이 공유 컨테이너를 stop()시켜버려 같은 JVM에서 실행되는 다음 서브클래스가 죽은 컨테이너에 연결을 시도하다 실패한다.

**Colima로 로컬 Docker를 쓰는 경우** 아래 두 가지가 필요하고, 둘 다 이미 처리돼 있다:
- `/var/run/docker.sock`이 Testcontainers가 하드코딩해서 찾는 기본 경로라, Colima 소켓을 여기로 심볼릭 링크해야 한다: `sudo ln -sf ~/.colima/default/docker.sock /var/run/docker.sock` (최초 1회, sudo 필요).
- `build.gradle`의 `test` 태스크에 Colima 호환용 설정이 이미 들어있다 — `user.home`/`DOCKER_HOST` 포워딩(Gradle이 포크된 테스트 JVM에 기본으로 전달하지 않음), `api.version=1.44` 고정(Testcontainers 1.20.4의 오래된 기본 API 버전이 최신 Docker 엔진에서 거부됨), `TESTCONTAINERS_RYUK_DISABLED=true`(Ryuk이 macOS 호스트 소켓 경로를 게스트 VM 안으로 마운트하려다 실패하기 때문 — Colima 구조상 원천적으로 안 됨). 이 설정들을 "정리"라고 지우지 말 것.

## 패키지 구조

도메인별 구조. `src/main/java/com/baseball/ticketing/` 하위:

| 패키지 | 담당 | 상태 |
|---|---|---|
| `member` | 회원가입/로그인 대상 도메인 | 엔티티(`Member`, `MemberRole`) 구현 완료 |
| `game` | 경기, 구장, 구역(`section`), 좌석(`seat`/`game_seat`) 조회·관리 | 엔티티(`Stadium`, `Section`, `Seat`, `Game`, `GameStatus`, `GameSeat`, `GameSeatStatus`) 구현 완료 |
| `reservation` | 예매, HOLD — 핵심 동시성 로직이 위치하는 곳 | 엔티티(`Reservation`, `ReservationStatus`, `ReservationSeat`) 구현 완료 |
| `payment` | 모의 결제 | 엔티티(`Payment`, `PaymentMethod`, `PaymentStatus`) 구현 완료 |
| `admin` | 관리자 전용 엔드포인트 | 미착수 |
| `auth` | JWT 발급/검증, Security 설정 | 미착수 |
| `common` | 공통 예외 처리, 응답 래핑 등 | 미착수 |

## 현재 상태

Spring Boot 프로젝트 스캐폴딩(`build.gradle`, 패키지 구조, `application.yml`, `docker-compose.yml`)과 ERD 9개 테이블 전체에 대응하는 JPA 엔티티 구현이 끝났다. `GameSeat`에는 `@Version` 낙관적 락과 `UNIQUE(game_id, seat_id)`, `ReservationSeat`에는 `UNIQUE(game_seat_id)`가 걸려 있고, 상태 전이는 엔티티 메서드(`hold()/sell()/release()`, `confirm()/cancel()/expire()`, `complete()/fail()/refund()`)로 캡슐화했다.

`./gradlew test`로 단위 테스트 32개 + Testcontainers 기반 DB 제약/낙관적 락 테스트 8개(위 "테스트 전략" 참고) 전부 통과 확인됨(로컬 Colima + `docker-compose.yml` 대상). Repository, 인증(JWT), 실제 API 엔드포인트(hold/payment 등 로드맵 4~7단계)는 아직 미구현 — 다음 단계.

> 참고: `application.yml`의 `spring.jpa.hibernate.ddl-auto: update`는 스캐폴딩 단계 임시값이다. Repository/API 구현 시 Flyway/Liquibase 도입 여부를 재검토할 것.
