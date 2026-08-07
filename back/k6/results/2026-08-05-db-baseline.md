# k6 오픈런 스파이크 — 1막 DB 락 기준선 (2026-08-05)

2막 Redis 게이트 전후 비교의 기준선 측정. redis 모드 측정은 로컬 Redis 설치 후 진행 예정.

## 측정 환경

- Windows 11 Home, 단일 머신 (부하 발생·서버·DB 동일 호스트 — 절대값보다 전후 상대 비교용)
- 백엔드: `gradlew bootRun`, Java 21 (Zulu), `tably.slot-hold.mode=db`
- DB: PostgreSQL 18.3 로컬 (전용 스크래치 클러스터, 포트 5433)
- k6 v2.1.0, 시나리오 기본값: VU 1,000 / 슬롯 20 / 워밍업 30초

## 실행

```powershell
k6 run seed-users.js                                        # 1,000계정, 실패 0
k6 run --summary-export=results/spike-db.json open-run-spike.js
psql -U postgres -p 5433 -d tably -f verify.sql             # 중복 예약 0건 확인
psql -U postgres -p 5433 -d tably -f cleanup.sql            # 선점 20건 해제
```

## 결과 (db 모드)

| 지표 | 값 |
|---|---|
| `http_req_duration{operation:hold}` p95 | **444.93ms** (avg 308.72ms, max 460.58ms) |
| `hold_success` | 20 (= 슬롯 수, 초과 없음) |
| `hold_conflict` (409) | 980 |
| `hold_unexpected` (500·락 타임아웃) | 0 |
| `login_failed` | 0 |
| verify.sql 중복 예약 | 0건 |

요약 JSON: [spike-db.json](spike-db.json)
