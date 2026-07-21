import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getPolicy, getRestaurant } from '../api'
import { describeRefundRule } from '../lib/refund'

// 시드 슬롯이 다음 달에 생성되므로 기본 날짜는 다음 달 15일
function defaultDate() {
  const d = new Date()
  d.setMonth(d.getMonth() + 1, 15)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

// "MONTHLY:1:10:00" → "매월 1일 10:00에 다음 달 예약 오픈"
function describeOpenRule(openRule) {
  const [kind, day, ...time] = openRule.split(':')
  if (kind === 'MONTHLY') return `매월 ${day}일 ${time.join(':')}에 다음 달 예약 오픈`
  return openRule
}

export default function RestaurantPage() {
  const { id } = useParams()
  const restaurantId = Number(id)
  const [ctx, setCtx] = useState(null) // { restaurant, policy }
  const [error, setError] = useState(null)
  const [date, setDate] = useState(defaultDate)

  useEffect(() => {
    let stale = false
    Promise.all([getRestaurant(restaurantId), getPolicy(restaurantId)]).then(
      ([restaurant, policy]) => {
        if (!stale) setCtx({ restaurant, policy })
      },
      (e) => {
        if (!stale) setError(e.message)
      },
    )
    return () => {
      stale = true
    }
  }, [restaurantId])

  if (error) return <div className="banner error">{error}</div>
  if (!ctx) return <p className="hint">불러오는 중…</p>

  const { restaurant, policy } = ctx
  return (
    <section className="page">
      <div className="detail-head">
        <h2 className="section-title">{restaurant.name}</h2>
        <span className="deposit-badge">예약금 {policy.depositPerPerson.toLocaleString()}원/인</span>
      </div>

      <div className="info-card">
        <h3>영업 안내</h3>
        <div className="time-chips">
          {policy.slotTimes.split(',').map((t) => (
            <span key={t} className="chip">
              {t.trim()}
            </span>
          ))}
          <span className="chip muted">타임당 테이블 {policy.tablesPerTime}개</span>
        </div>
        <p className="open-rule">{describeOpenRule(policy.openRule)}</p>
      </div>

      <div className="info-card">
        <h3>예약금·환불 규정</h3>
        <p className="policy-note">
          노쇼 방지를 위해 인원수 × {policy.depositPerPerson.toLocaleString()}원의 예약금을
          결제합니다. 정상 방문 시 전액 환불됩니다.
        </p>
        <ul className="refund-table">
          {describeRefundRule(policy.refundRule).map((row) => (
            <li key={row.label}>
              <span>{row.label}</span>
              <strong className={row.percent === 0 ? 'zero' : ''}>{row.percent}% 환불</strong>
            </li>
          ))}
        </ul>
      </div>

      <div className="info-card">
        <h3>방문 날짜</h3>
        <input
          type="date"
          className="date-input"
          value={date}
          onChange={(e) => setDate(e.target.value)}
        />
      </div>

      <div className="entry-buttons">
        <Link to={`/restaurants/${restaurantId}/reserve?date=${date}`} className="primary-link">
          이 날짜로 예약하기
        </Link>
        <Link to={`/restaurants/${restaurantId}/waiting`} className="secondary-link">
          오늘 웨이팅 등록
        </Link>
      </div>
    </section>
  )
}
