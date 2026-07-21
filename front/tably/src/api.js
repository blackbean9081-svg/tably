// tably API 레이어 (단일 파일)
//
// 실제 백엔드(http://localhost:8080, vite dev 프록시 /api 경유) 호출이 기본이고,
// 백엔드 핵심영역이 미구현(UnsupportedOperationException → 500)인 아래 3건만 목(mock)이다:
//   - 슬롯 선점  POST /api/reservations   (핵심영역 1) → 전체 목
//   - 예약금 결제 POST /api/payments       (핵심영역 2) → 전체 목
//   - 웨이팅 등록 POST /api/waitings       (핵심영역 6) → 실제 시도 후 500이면 목 폴백
// 각 함수의 "백엔드 구현 후 실제 호출로 교체" 주석을 참고해 교체하면 된다.
//
// 응답 envelope: { success: true, data } | { success: false, error: { code, message } }

const SEED_ACCOUNT = { email: 'guest@tably.com', password: 'password123!' }

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message)
    this.status = status
    this.code = code
  }
}

// ── 공통: 자동 로그인 + 인증 fetch ───────────────────────────────────
let accessToken = null

async function login() {
  const res = await fetch('/api/members/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(SEED_ACCOUNT),
  })
  let body = null
  try {
    body = await res.json()
  } catch {
    // 비정상 응답(빈 본문 등)은 아래 공통 에러로 처리
  }
  if (!res.ok || !body?.success) {
    throw new ApiError(
      res.status,
      body?.error?.code ?? 'LOGIN_FAILED',
      body?.error?.message ?? '자동 로그인에 실패했습니다. 백엔드 상태를 확인해주세요.',
    )
  }
  accessToken = body.data.accessToken
}

// envelope 해석 + Bearer 첨부. 토큰 만료(401) 시 1회 재로그인 후 재시도.
async function request(path, options = {}, retried = false) {
  if (!accessToken) await login()
  const res = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
      ...options.headers,
    },
  })
  if (res.status === 401 && !retried) {
    accessToken = null
    return request(path, options, true)
  }
  let body = null
  try {
    body = await res.json()
  } catch {
    // 본문 없는 오류 응답
  }
  if (!res.ok || !body?.success) {
    throw new ApiError(
      res.status,
      body?.error?.code ?? 'UNKNOWN',
      body?.error?.message ?? `요청에 실패했습니다. (HTTP ${res.status})`,
    )
  }
  return body.data
}

// ── 실제 API ─────────────────────────────────────────────────────────

// 앱 시작 시: 자동 로그인 → 「스시 준」 식당·예약 정책 조회
export async function bootstrap() {
  await login()
  const restaurants = await request('/api/restaurants')
  const restaurant = restaurants.find((r) => r.name === '스시 준') ?? restaurants[0]
  if (!restaurant) {
    throw new ApiError(404, 'RESTAURANT_NOT_FOUND', '시드 식당이 없습니다. 백엔드 시드 데이터를 확인해주세요.')
  }
  const policy = await request(`/api/restaurants/${restaurant.id}/policy`)
  return { restaurant, policy }
}

// 슬롯 조회 — 실제 API.
// 단, 목으로 선점된 슬롯은 백엔드가 모르므로 CLOSED로 덧씌워 화면 흐름을 유지한다.
// (선점이 실제 구현되면 mockClosedSlotIds 덧씌우기는 제거)
const mockClosedSlotIds = new Set()

export async function getSlots(restaurantId, date) {
  const slots = await request(`/api/restaurants/${restaurantId}/slots?date=${date}`)
  return slots.map((s) => (mockClosedSlotIds.has(s.id) ? { ...s, status: 'CLOSED' } : s))
}

// ── 목 폴백 ──────────────────────────────────────────────────────────
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const HOLD_DURATION_MS = 10 * 60 * 1000 // 선점 후 결제 대기 10분 (FR-05)
let mockSeq = -1 // 실제 백엔드 id(양수)와 구분되도록 음수 사용
const mockReservations = new Map()

// [목] 슬롯 선점 — 핵심영역 1 미구현(500)이라 전체를 목으로 대체했다.
// 백엔드 구현 후 실제 호출로 교체:
//   const reservationId = await request('/api/reservations', {
//     method: 'POST',
//     body: JSON.stringify({ slotId: slot.id, partySize }),
//   })
//   return request(`/api/reservations/${reservationId}`)
//   // 409 SLOT_ALREADY_TAKEN / SLOT_CLOSED → "방금 마감되었습니다" 처리
export async function holdSlot(slot, partySize, depositAmount) {
  await delay(300)
  if (slot.status !== 'OPEN' || mockClosedSlotIds.has(slot.id)) {
    throw new ApiError(409, 'SLOT_ALREADY_TAKEN', '방금 마감되었습니다.')
  }
  mockClosedSlotIds.add(slot.id)
  const reservation = {
    id: mockSeq--,
    slotId: slot.id,
    slotDate: slot.slotDate,
    slotTime: slot.slotTime,
    tableNo: slot.tableNo,
    partySize,
    depositAmount,
    status: 'PENDING_PAYMENT',
    expiresAt: Date.now() + HOLD_DURATION_MS,
  }
  mockReservations.set(reservation.id, reservation)
  return { ...reservation }
}

// [목] 예약금 결제 — 핵심영역 2 미구현(500)이라 전체를 목으로 대체했다.
// 백엔드 구현 후 실제 호출로 교체:
//   await request('/api/payments', {
//     method: 'POST',
//     body: JSON.stringify({ reservationId, amount, idempotencyKey }),
//   })
//   return request(`/api/reservations/${reservationId}`)
//   // 409 PAYMENT_TIME_EXPIRED → 만료 안내, 400 PAYMENT_AMOUNT_MISMATCH → 금액 오류
export async function payDeposit({ reservationId, amount, idempotencyKey }) {
  await delay(700) // PG 승인 지연 흉내
  void idempotencyKey // 실제 API 계약(PaymentApproveRequestDto)에 필요한 필드 — 목에서는 미사용
  const reservation = mockReservations.get(reservationId)
  if (!reservation) throw new ApiError(404, 'RESERVATION_NOT_FOUND', '존재하지 않는 예약입니다.')
  if (reservation.status === 'CONFIRMED') return { ...reservation } // 멱등 응답
  if (Date.now() > reservation.expiresAt) {
    reservation.status = 'EXPIRED'
    mockClosedSlotIds.delete(reservation.slotId)
    throw new ApiError(409, 'PAYMENT_TIME_EXPIRED', '선점 시간이 만료되었습니다. 슬롯을 다시 선택해주세요.')
  }
  if (amount !== reservation.depositAmount) {
    throw new ApiError(400, 'PAYMENT_AMOUNT_MISMATCH', '결제 금액이 일치하지 않습니다.')
  }
  reservation.status = 'CONFIRMED'
  return { ...reservation }
}

// ── 웨이팅 (② 화면에서 사용) ─────────────────────────────────────────
let mockWaiting = null

// 웨이팅 등록 — 실제 API를 먼저 시도하고, 핵심영역 6 미구현(500)이면 목으로 폴백한다.
// 백엔드 구현 후: catch의 폴백 분기를 제거하면 된다.
export async function registerWaiting(restaurantId, restaurantName) {
  try {
    return await request('/api/waitings', {
      method: 'POST',
      body: JSON.stringify({ restaurantId }),
    })
  } catch (e) {
    if (e instanceof ApiError && e.status === 500) {
      // [목 폴백] WaitingResponseDto 형태를 그대로 흉내 낸다
      mockWaiting = {
        id: -1,
        restaurantId,
        restaurantName,
        waitingNo: 12,
        status: 'WAITING', // WAITING | CALLED | SEATED | EXPIRED | CANCELED
        calledAt: null,
        aheadCount: 7,
        _registeredAt: Date.now(),
      }
      return publicWaiting()
    }
    throw e
  }
}

// 내 순번 조회(3초 폴링) — 실제 API.
// 단, 등록이 목으로 폴백된 건(id 음수)은 백엔드에 없으므로 목으로 폴링한다.
// 백엔드 등록 구현 후: 목 분기(id < 0)를 제거하면 된다.
export async function getWaitingStatus(waitingId) {
  if (waitingId > 0) {
    return request(`/api/waitings/${waitingId}`)
  }
  await delay(150)
  if (!mockWaiting || mockWaiting.id !== waitingId) {
    throw new ApiError(404, 'WAITING_NOT_FOUND', '존재하지 않는 웨이팅입니다.')
  }
  const w = mockWaiting
  if (w.status === 'WAITING') {
    // 시연용: 8초마다 앞 팀이 한 팀씩 빠지고, 0팀이 되면 사장이 호출한 것으로 간주
    const elapsed = Date.now() - w._registeredAt
    w.aheadCount = Math.max(0, 7 - Math.floor(elapsed / 8000))
    if (w.aheadCount === 0) {
      w.status = 'CALLED'
      w.calledAt = new Date().toISOString()
    }
  }
  if (w.status === 'CALLED' && Date.now() - new Date(w.calledAt).getTime() > 10 * 60 * 1000) {
    w.status = 'EXPIRED' // 호출 후 10분 내 도착 확인 없음 → 순번 넘어감
  }
  return publicWaiting()
}

function publicWaiting() {
  const { id, restaurantId, restaurantName, waitingNo, status, calledAt, aheadCount } = mockWaiting
  return { id, restaurantId, restaurantName, waitingNo, status, calledAt, aheadCount }
}
