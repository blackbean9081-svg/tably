# k6 부하 테스트 — 오픈런 스파이크

NFR 검증: **슬롯 20개 / 동시 1,000명** 오픈런에서 중복 예약 0건, 실패 응답 포함 p95 측정.
여기서 얻는 수치가 2막 Redis 전환의 근거(전/후 비교 기준선)가 된다.

## 준비

1. k6 설치 (Windows):
   ```
   winget install k6 --source winget
   ```
2. 백엔드 기동 (`application-local.properties` 필요):
   ```
   cd back && ./gradlew bootRun
   ```
   시드 데이터(DataInitializer)가 다음 달 슬롯을 만들어 둔 상태여야 한다.

## 실행 순서

```powershell
cd back/k6

# 1) 부하 테스트 계정 1,000개 생성 (재실행 안전 — 이미 있으면 409 무시)
k6 run seed-users.js

# 2) 오픈런 스파이크 (요약 JSON 저장 권장 — README 기록용)
mkdir results -Force
k6 run --summary-export=results/spike.json open-run-spike.js

# 3) DB 검증 — 1번 쿼리 결과가 0행이면 중복 예약 0건
psql -U postgres -d tably -f verify.sql
```

## 시나리오 구조

- 워밍업(기본 30초): VU 1,000명이 각자 로그인 (BCrypt 비용을 스파이크 측정에서 분리)
- 전원이 같은 wall-clock 시각까지 대기 → **일제히 선점 요청** ("10:00 정각" 재현)
- VU를 슬롯 20개에 라운드로빈 배정 → 슬롯당 약 50명 경합

## 판정 기준 (thresholds)

| 지표 | 기준 | 의미 |
|---|---|---|
| `login_failed` | 0건 | 워밍업 로그인 전원 성공 |
| `hold_unexpected` | 0건 | 선점 응답은 201 또는 409(SLOT_ALREADY_TAKEN)뿐 — 500/락 타임아웃 없음 |
| `hold_success` | ≤ 슬롯 수 | 초과하면 중복 예약 발생 (최종 판정은 verify.sql) |
| `http_req_duration{operation:hold}` | 측정만 | 실패 응답 포함 p95 — README에 기록할 수치 |

## 옵션

`-e KEY=VALUE`로 조정: `BASE_URL`(기본 localhost:8080), `USERS`(1000), `SLOTS`(20),
`RESTAURANT_ID`(1), `DATE`(다음 달 1일), `WARMUP_S`(30)

## 재실행

성공한 선점은 PENDING_PAYMENT 상태로 슬롯을 10분간 점유한다.
만료 배치(HoldExpiryScheduler)를 기다리거나 `cleanup.sql`로 즉시 해제 후 재실행.
