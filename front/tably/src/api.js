// tably API 레이어 — 현재는 전부 목(mock) 구현이다.
// 백엔드가 아직 미구현이라 화면 개발용으로 메모리 상의 가짜 데이터를 반환한다.
// 계약 초안 근거: docs/requirements.md FR-03~07(예약·결제), FR-14~16(웨이팅).
// 백엔드 완성 후 이 파일의 각 함수 본문만 실제 fetch로 교체하면
// 화면 코드는 수정 없이 그대로 연동된다.

export const RESTAURANT = { id: 1, name: '스시 준', depositPerPerson: 20000 }

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

const HOLD_DURATION_MS = 10 * 60 * 1000 // 슬롯 선점 유지 시간 10분 (FR-05)
const CALL_DURATION_MS = 10 * 60 * 1000 // 웨이팅 호출 후 도착 대기 10분 (FR-16)
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// ── 목 저장소 (모듈 메모리 — 새로고침하면 초기화된다) ─────────────────
const TIMES = ['18:00', '20:30']
const TABLE_COUNT = 4
const slotsByDate = new Map() // date → slot[]
const reservations = new Map() // reservationId → reservation
let nextReservationId = 1

function slotsFor(date) {
  if (!slotsByDate.has(date)) {
    const slots = []
    for (const time of TIMES) {
      for (let tableNo = 1; tableNo <= TABLE_COUNT; tableNo++) {
        slots.push({
          slotId: `${date}-${time}-${tableNo}`,
          time,
          tableNo,
          // 시연용 초기 상태: 20:30 타임 2·3번 테이블은 이미 마감
          status:
            time === '20:30' && (tableNo === 2 || tableNo === 3)
              ? 'RESERVED'
              : 'AVAILABLE',
          // 18:00 3번 테이블은 목록에서는 비어 보이지만 선점 시도 순간
          // 다른 손님이 한발 앞선 오픈런 경쟁 상황(409)을 재현한다
          _sniped: time === '18:00' && tableNo === 3,
        })
      }
    }
    slotsByDate.set(date, slots)
  }
  return slotsByDate.get(date)
}

function findSlot(slotId) {
  return slotsFor(slotId.slice(0, 10)).find((s) => s.slotId === slotId)
}

// 슬롯 조회 (FR-03)
// 백엔드 완성 후 실제 fetch로 교체:
//   GET /api/restaurants/{restaurantId}/slots?date=YYYY-MM-DD
export async function getSlots(restaurantId, date) {
  await delay(250)
  return slotsFor(date).map(({ _sniped, ...slot }) => ({ ...slot }))
}

// 슬롯 선점 (FR-04) — 이미 선점된 슬롯이면 409 "방금 마감되었습니다"
// 백엔드 완성 후 실제 fetch로 교체:
//   POST /api/reservations  body: { slotId, partySize }
export async function holdSlot(slotId, partySize) {
  await delay(400)
  const slot = findSlot(slotId)
  if (!slot) throw new ApiError(404, '존재하지 않는 슬롯입니다.')
  if (slot._sniped && slot.status === 'AVAILABLE') {
    slot.status = 'RESERVED' // 다른 손님이 한발 먼저 선점한 상황
    throw new ApiError(409, '방금 마감되었습니다.')
  }
  if (slot.status !== 'AVAILABLE') throw new ApiError(409, '방금 마감되었습니다.')
  slot.status = 'HELD'
  const reservation = {
    reservationId: nextReservationId++,
    slotId,
    date: slotId.slice(0, 10),
    time: slot.time,
    tableNo: slot.tableNo,
    partySize,
    depositAmount: RESTAURANT.depositPerPerson * partySize,
    status: 'PENDING_PAYMENT',
    expiresAt: Date.now() + HOLD_DURATION_MS,
  }
  reservations.set(reservation.reservationId, reservation)
  return { ...reservation }
}

// 예약금 결제 (FR-06) — 이미 결제된 예약이면 같은 결과를 다시 반환(멱등성)
// 백엔드 완성 후 실제 fetch로 교체:
//   POST /api/reservations/{reservationId}/payment
export async function payDeposit(reservationId) {
  await delay(700) // PG 승인 지연 흉내
  const reservation = reservations.get(reservationId)
  if (!reservation) throw new ApiError(404, '존재하지 않는 예약입니다.')
  if (reservation.status === 'CONFIRMED') return { ...reservation }
  if (Date.now() > reservation.expiresAt) {
    reservation.status = 'EXPIRED'
    const slot = findSlot(reservation.slotId)
    if (slot.status === 'HELD') slot.status = 'AVAILABLE'
    throw new ApiError(409, '선점 시간이 만료되었습니다. 슬롯을 다시 선택해주세요.')
  }
  reservation.status = 'CONFIRMED'
  findSlot(reservation.slotId).status = 'RESERVED'
  return { ...reservation }
}

// ── 웨이팅 (FR-14~16) — 2단계 웨이팅 화면에서 사용 ───────────────────
let currentWaiting = null

// 웨이팅 등록 (FR-14)
// 백엔드 완성 후 실제 fetch로 교체:
//   POST /api/waitings  body: { restaurantId, partySize }
export async function registerWaiting(restaurantId, partySize) {
  await delay(300)
  currentWaiting = {
    waitingId: 1,
    number: 12, // 내 대기 번호
    partySize,
    aheadCount: 7, // 내 앞 팀 수
    status: 'WAITING', // WAITING | CALLED | EXPIRED
    _registeredAt: Date.now(),
    _calledAt: null,
  }
  const { _registeredAt, _calledAt, ...pub } = currentWaiting
  return { ...pub }
}

// 내 순번 조회 — 폴링용 (FR-15), 호출·만료 상태 반영 (FR-16)
// 백엔드 완성 후 실제 fetch로 교체:
//   GET /api/waitings/{waitingId}
export async function getWaitingStatus(waitingId) {
  await delay(150)
  if (!currentWaiting || currentWaiting.waitingId !== waitingId) {
    throw new ApiError(404, '존재하지 않는 웨이팅입니다.')
  }
  const w = currentWaiting
  if (w.status === 'WAITING') {
    // 시연용: 8초마다 앞 팀이 한 팀씩 빠지고, 0팀이 되면 사장이 호출한다
    const elapsed = Date.now() - w._registeredAt
    w.aheadCount = Math.max(0, 7 - Math.floor(elapsed / 8000))
    if (w.aheadCount === 0) {
      w.status = 'CALLED'
      w._calledAt = Date.now()
    }
  }
  if (w.status === 'CALLED' && Date.now() - w._calledAt > CALL_DURATION_MS) {
    w.status = 'EXPIRED' // 호출 후 10분 내 도착 확인 없음 → 순번 넘어감
  }
  const { _registeredAt, _calledAt, ...pub } = w
  return { ...pub }
}
