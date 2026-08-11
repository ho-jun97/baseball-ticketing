# CLAUDE.md

## 프로젝트 개요

야구 좌석예매 시스템. 핵심 과제는 **동일 좌석에 대한 동시 예매 요청을 안전하게 처리하는 것**이다 — 기능을 추가하거나 리팩터링할 때도 이 목표를 깨뜨리지 않는지 항상 확인한다.

설계 문서:
- ERD: `docs/design/erd.md`
- API 명세: `docs/design/api-spec.md`

## 기술 스택

- Java + Spring Boot + JPA/Hibernate
- DB: 미정(TBD) — 임의로 특정 DB(MySQL/PostgreSQL 등)를 전제하지 말고, 필요하면 사용자에게 확인한다

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

## 현재 상태

설계 완료, 구현 착수 전 — 저장소에 실제 코드가 없다. 코드 스캐폴딩이 생기면 이 파일에 빌드/실행/테스트 명령과 패키지 구조 섹션을 추가한다.
