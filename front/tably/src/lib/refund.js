// 환불 규정(refundRule) 클라이언트 계산 유틸
//
// 백엔드 RefundCalculator와 같은 규칙을 프론트에서 재현한다 (환불액 "사전 고지"용).
// 서버에 preview API가 없어 클라이언트 계산으로 확정함 (2026-07-21 백엔드 개발자 결정).
// 규칙 문자열 형식: "days:percent,days:percent,..." 예) "7:100,3:50,1:0"
//   → 방문 D-7 이전 취소 100% / D-3 이전 50% / D-1 이전 0%, 최소 기준 미만(당일 등)은 0%

// "7:100,3:50,1:0" → [{ days: 7, percent: 100 }, ...] (days 내림차순)
export function parseRefundRule(rule) {
  return rule
    .split(',')
    .map((part) => {
      const [days, percent] = part.split(':').map(Number)
      return { days, percent }
    })
    .sort((a, b) => b.days - a.days)
}

// 방문일까지 남은 일수(날짜 차이, 시각 무시)
export function daysUntil(dateStr) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const target = new Date(`${dateStr}T00:00:00`)
  return Math.round((target - today) / (24 * 60 * 60 * 1000))
}

// 남은 일수에 적용되는 환불율(%). 가장 큰 기준부터 검사, 최소 기준 미만은 0%
export function refundPercentFor(rule, daysLeft) {
  for (const { days, percent } of parseRefundRule(rule)) {
    if (daysLeft >= days) return percent
  }
  return 0
}

// 사전 고지용 환불액 (원 단위 정수, 0~결제액 범위)
export function refundAmountFor(rule, daysLeft, paidAmount) {
  const amount = Math.floor((paidAmount * refundPercentFor(rule, daysLeft)) / 100)
  return Math.min(Math.max(amount, 0), paidAmount)
}

// 안내 문구용: [{ label: "7일 전까지", percent: 100 }, ..., { label: "그 이후", percent: 0 }]
export function describeRefundRule(rule) {
  const rows = parseRefundRule(rule).map(({ days, percent }) => ({
    label: `방문 ${days}일 전까지`,
    percent,
  }))
  const minPercent = parseRefundRule(rule).at(-1)
  if (!minPercent || minPercent.percent !== 0) {
    rows.push({ label: '그 이후', percent: 0 })
  }
  return rows
}
