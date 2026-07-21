# tably ERD v1 (2026-07-21 확정)

## 설계 3원칙

1. 예약(reservation)과 결제(payment)는 분리 — 결제 시도 1번 = payment 1행, 사건은 쌓는다
2. 슬롯은 오픈 시점에 사전 생성 — 락 대상 확보, 빈자리 조회 단순화, 상태 관리
3. 상태의 주인은 reservation.status — slot.status는 OPEN/CLOSED만 (중복 저장 금지)

```mermaid
erDiagram
  MEMBER ||--o{ RESTAURANT : owns
  RESTAURANT ||--|| RESERVATION_POLICY : has
  RESTAURANT ||--o{ SLOT : generates
  SLOT ||--o{ RESERVATION : "booked as"
  MEMBER ||--o{ RESERVATION : makes
  RESERVATION ||--o{ PAYMENT : "attempts"
  RESTAURANT ||--o{ WAITING : queues
  MEMBER ||--o{ WAITING : joins
  MEMBER ||--o{ NOTIFICATION : receives
  MEMBER { bigint id PK  string email "unique"  string password  string name  string role "GUEST/OWNER" }
  RESTAURANT { bigint id PK  bigint owner_id FK  string name }
  RESERVATION_POLICY { bigint id PK  bigint restaurant_id FK  int deposit_per_person  string refund_rule  string open_rule  int tables_per_time }
  SLOT { bigint id PK  bigint restaurant_id FK  date slot_date  time slot_time  int table_no  string status "OPEN/CLOSED" }
  RESERVATION { bigint id PK  bigint slot_id FK  bigint member_id FK  int party_size  string status  datetime held_at }
  PAYMENT { bigint id PK  bigint reservation_id FK  string type "PAY/REFUND"  int amount  string status  string idempotency_key "unique"  string pg_tx_id }
  WAITING { bigint id PK  bigint restaurant_id FK  bigint member_id FK  int waiting_no  string status  datetime called_at }
  NOTIFICATION { bigint id PK  bigint member_id FK  string event_type  string status  datetime sent_at }
```

정산(settlement), 이의신청(appeal) 테이블은 해당 기능 착수 시 추가.
