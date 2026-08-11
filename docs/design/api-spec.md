# API 명세 — 야구 좌석예매 시스템

베이스 경로: `/api`
인증: `Authorization: Bearer {JWT}` (로그인/회원가입, 경기·좌석 조회 GET 제외 전부 필요)

> **정책**: 좌석 선점(HOLD)의 TTL은 5분이다. TTL이 지나면 스케줄러가 주기적으로 스캔해 해당 `game_seat`를 AVAILABLE로 되돌리고 연결된 예매를 EXPIRED 처리한다(4주차 구현).

---

## 1. 인증

### `POST /api/auth/signup`
회원가입.

**Request**
```json
{
  "email": "user@example.com",
  "password": "string",
  "name": "string",
  "phone": "010-0000-0000"
}
```
**Response** `201 Created`
```json
{ "memberId": 1, "email": "user@example.com" }
```

### `POST /api/auth/login`
로그인, JWT 발급.

**Request**
```json
{ "email": "user@example.com", "password": "string" }
```
**Response** `200 OK`
```json
{ "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 3600 }
```

---

## 2. 경기 / 좌석 조회 (인증 불필요)

### `GET /api/games?date=&team=`
경기 목록 조회. `date`(YYYY-MM-DD), `team` 쿼리 파라미터로 필터링.

**Response** `200 OK`
```json
{
  "games": [
    {
      "gameId": 1,
      "homeTeam": "string",
      "awayTeam": "string",
      "gameDatetime": "2026-08-15T18:30:00",
      "stadiumName": "string",
      "status": "SCHEDULED"
    }
  ]
}
```

### `GET /api/games/{gameId}`
경기 상세 조회.

**Response** `200 OK`
```json
{
  "gameId": 1,
  "homeTeam": "string",
  "awayTeam": "string",
  "gameDatetime": "2026-08-15T18:30:00",
  "stadium": { "stadiumId": 1, "name": "string", "address": "string" },
  "status": "SCHEDULED"
}
```

### `GET /api/games/{gameId}/seats`
경기의 구역별 좌석 배치와 실시간 상태 조회.

**Response** `200 OK`
```json
{
  "sections": [
    {
      "sectionId": 1,
      "name": "1루 응원석",
      "price": 15000,
      "seats": [
        { "gameSeatId": 101, "seatRow": "A", "seatNumber": "1", "status": "AVAILABLE" },
        { "gameSeatId": 102, "seatRow": "A", "seatNumber": "2", "status": "SOLD" }
      ]
    }
  ]
}
```

---

## 3. 예매 (핵심 동시성 구간)

### `POST /api/games/{gameId}/reservations/hold`
좌석을 임시 선점한다. 이 엔드포인트가 동시성 제어 데모의 중심 — 동일 `gameSeatId`에 대한 동시 요청 중 하나만 성공해야 한다.

**Request**
```json
{ "seatIds": [101, 102] }
```
**Response** `201 Created`
```json
{
  "reservationId": 55,
  "status": "PENDING",
  "totalPrice": 30000,
  "holdExpireAt": "2026-08-10T12:35:00"
}
```
**에러**
- `409 Conflict` — 요청한 좌석 중 하나 이상이 이미 HOLD/SOLD 상태 (`SEAT_ALREADY_TAKEN`)

### `POST /api/reservations/{reservationId}/payments`
모의 결제 승인 → 예매 확정. `reservation.status: PENDING→CONFIRMED`, 연결된 `game_seat.status: HOLD→SOLD`.

**Request**
```json
{ "method": "MOCK" }
```
**Response** `200 OK`
```json
{
  "reservationId": 55,
  "status": "CONFIRMED",
  "paymentId": 200,
  "paidAt": "2026-08-10T12:32:00"
}
```
**에러**
- `410 Gone` — HOLD TTL 만료 (`HOLD_EXPIRED`)

### `DELETE /api/reservations/{reservationId}`
예매 취소. PENDING이면 선점 해제(HOLD→AVAILABLE), CONFIRMED면 취소 처리(SOLD→AVAILABLE, 결제 REFUNDED).

**Response** `200 OK`
```json
{ "reservationId": 55, "status": "CANCELED" }
```

### `GET /api/reservations/{reservationId}`
예매 상세 조회.

**Response** `200 OK`
```json
{
  "reservationId": 55,
  "status": "CONFIRMED",
  "game": { "gameId": 1, "homeTeam": "string", "awayTeam": "string", "gameDatetime": "..." },
  "seats": [ { "gameSeatId": 101, "sectionName": "1루 응원석", "seatRow": "A", "seatNumber": "1" } ],
  "totalPrice": 30000,
  "createdAt": "...",
  "confirmedAt": "..."
}
```

### `GET /api/members/me/reservations`
내 예매 목록 조회 (로그인 필요).

**Response** `200 OK`
```json
{ "reservations": [ { "reservationId": 55, "gameId": 1, "status": "CONFIRMED", "totalPrice": 30000 } ] }
```

---

## 4. 관리자 (role: ADMIN 필요)

### `POST /api/admin/games`
경기 등록.

**Request**
```json
{
  "stadiumId": 1,
  "homeTeam": "string",
  "awayTeam": "string",
  "gameDatetime": "2026-08-15T18:30:00"
}
```
**Response** `201 Created`
```json
{ "gameId": 1, "status": "SCHEDULED" }
```

### `POST /api/admin/games/{gameId}/seats/init`
구장 좌석맵(`seat` 테이블) 기준으로 해당 경기의 `game_seat`를 전량 AVAILABLE 상태로 일괄 생성.

**Response** `201 Created`
```json
{ "gameId": 1, "createdSeatCount": 3000 }
```

### `GET /api/admin/reservations`
전체 예매 조회 (페이지네이션).

**Response** `200 OK`
```json
{
  "reservations": [ { "reservationId": 55, "memberEmail": "user@example.com", "gameId": 1, "status": "CONFIRMED" } ],
  "page": 0,
  "totalElements": 1
}
```

---

## 엔드포인트 ↔ ERD 테이블 커버리지 확인

| 테이블 | 사용 엔드포인트 |
|---|---|
| member | signup, login, admin/reservations |
| stadium | games (상세), admin/games |
| section | games/{id}/seats |
| seat | admin/games/{id}/seats/init |
| game | games, games/{id}, admin/games |
| game_seat | games/{id}/seats, reservations/hold, reservations/{id}/payments, admin/games/{id}/seats/init |
| reservation | reservations/*, members/me/reservations, admin/reservations |
| reservation_seat | reservations/{id} (seats 목록) |
| payment | reservations/{id}/payments |

모든 테이블이 최소 1개 이상의 엔드포인트에서 사용됨을 확인 — 설계 누락 없음.
