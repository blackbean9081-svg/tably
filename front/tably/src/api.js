// tably API 레이어 (단일 파일)
//
// 모든 호출은 실제 백엔드(http://localhost:8080, vite dev 프록시 /api 경유)로 나간다.
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

// 앱 시작 시 자동 로그인 (시드 계정 — 로그인 화면 없음)
export async function ensureLogin() {
  if (!accessToken) await login()
}

// 식당 목록 (홈 화면)
export function getRestaurants() {
  return request('/api/restaurants')
}

// 식당 단건
export function getRestaurant(restaurantId) {
  return request(`/api/restaurants/${restaurantId}`)
}

// 예약 정책 (예약금 단가·환불 규정·타임)
export function getPolicy(restaurantId) {
  return request(`/api/restaurants/${restaurantId}/policy`)
}

// 슬롯 조회
export function getSlots(restaurantId, date) {
  return request(`/api/restaurants/${restaurantId}/slots?date=${date}`)
}

const HOLD_DURATION_MS = 10 * 60 * 1000 // 선점 후 결제 대기 10분 (FR-05)

// 슬롯 선점 — POST /api/reservations (응답: reservationId)
// 409 SLOT_ALREADY_TAKEN / SLOT_CLOSED → "방금 마감되었습니다" 처리
// ReservationResponseDto에는 결제 정보가 없어 화면 흐름에 필요한
// depositAmount(정책 계산값)·expiresAt(카운트다운용)을 클라이언트에서 보강한다.
// 만료 판정의 진실 원천은 서버(결제 시 PAYMENT_TIME_EXPIRED)다.
export async function holdSlot(slot, partySize, depositAmount) {
  const reservationId = await request('/api/reservations', {
    method: 'POST',
    body: JSON.stringify({ slotId: slot.id, partySize }),
  })
  const reservation = await request(`/api/reservations/${reservationId}`)
  return { ...reservation, depositAmount, expiresAt: Date.now() + HOLD_DURATION_MS }
}

// 예약금 결제 — POST /api/payments
// 409 PAYMENT_TIME_EXPIRED → 만료 안내, 400 PAYMENT_AMOUNT_MISMATCH → 금액 오류
export async function payDeposit({ reservationId, amount, idempotencyKey }) {
  await request('/api/payments', {
    method: 'POST',
    body: JSON.stringify({ reservationId, amount, idempotencyKey }),
  })
  const reservation = await request(`/api/reservations/${reservationId}`)
  return { ...reservation, depositAmount: amount }
}

// 내 예약 목록
export function getMyReservations() {
  return request('/api/reservations/my')
}

// 예약 취소 — POST /api/reservations/{id}/cancel (응답 data 없음)
export function cancelReservation(reservationId) {
  return request(`/api/reservations/${reservationId}/cancel`, { method: 'POST' })
}

// 예약별 결제 이력 — 실제 예약의 결제액 산정에 사용
export function getPaymentsFor(reservationId) {
  return request(`/api/payments/reservations/${reservationId}`)
}

// S4: 취소 전 환불액 사전 고지 — 서버 계산 (취소 확정과 같은 계산 경로).
// 응답: { reservationId, status, cancelable, paidAmount, refundRate, refundAmount, daysLeft, visitDate, refundRule }
export function getRefundPreview(reservationId) {
  return request(`/api/reservations/${reservationId}/refund-preview`)
}

// ── 웨이팅 (② 화면에서 사용) ─────────────────────────────────────────

// 웨이팅 등록 — POST /api/waitings (응답: WaitingResponseDto)
export function registerWaiting(restaurantId) {
  return request('/api/waitings', {
    method: 'POST',
    body: JSON.stringify({ restaurantId }),
  })
}

// 내 순번 조회(3초 폴링)
export function getWaitingStatus(waitingId) {
  return request(`/api/waitings/${waitingId}`)
}
