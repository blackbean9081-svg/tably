import { useEffect, useState } from 'react'
import { payDeposit } from '../api'

const HOLD_TOTAL_MS = 10 * 60 * 1000 // 선점 유지 시간 — 진행바 비율 계산용
const remainingOf = (expiresAt) => Math.max(0, expiresAt - Date.now())
const hhmm = (t) => t.slice(0, 5)

export default function PaymentPanel({ reservation, onPaid, onError, onExpire }) {
  const [remaining, setRemaining] = useState(() => remainingOf(reservation.expiresAt))
  const [paying, setPaying] = useState(false)
  // 결제 재시도 시에도 같은 키를 보내 이중 청구를 막는다 (PaymentApproveRequestDto.idempotencyKey)
  const [idempotencyKey] = useState(() => crypto.randomUUID())

  useEffect(() => {
    const timer = setInterval(() => setRemaining(remainingOf(reservation.expiresAt)), 1000)
    return () => clearInterval(timer)
  }, [reservation.expiresAt])

  useEffect(() => {
    if (remaining <= 0 && !paying) onExpire()
  }, [remaining, paying, onExpire])

  const minutes = String(Math.floor(remaining / 60000)).padStart(2, '0')
  const seconds = String(Math.floor((remaining % 60000) / 1000)).padStart(2, '0')

  const handlePay = async () => {
    if (paying) return // 중복 클릭 방지 (백엔드 멱등성과 별개의 UI 가드)
    setPaying(true)
    try {
      onPaid(
        await payDeposit({
          reservationId: reservation.id,
          amount: reservation.depositAmount,
          idempotencyKey,
        }),
      )
    } catch (e) {
      onError(e)
    }
  }

  return (
    <div className="panel">
      <h2>슬롯 선점 완료</h2>
      <dl className="detail">
        <div>
          <dt>일시</dt>
          <dd>
            {reservation.slotDate} {hhmm(reservation.slotTime)}
          </dd>
        </div>
        <div>
          <dt>테이블</dt>
          <dd>{reservation.tableNo}번</dd>
        </div>
        <div>
          <dt>인원</dt>
          <dd>{reservation.partySize}명</dd>
        </div>
        <div>
          <dt>예약금</dt>
          <dd>{reservation.depositAmount.toLocaleString()}원</dd>
        </div>
      </dl>
      <p className="countdown">
        남은 결제 시간
        <strong>
          {minutes}:{seconds}
        </strong>
      </p>
      <div className="countdown-bar">
        <div className="fill" style={{ width: `${(remaining / HOLD_TOTAL_MS) * 100}%` }} />
      </div>
      <button className="primary" onClick={handlePay} disabled={paying || remaining <= 0}>
        {paying ? '결제 중…' : `예약금 ${reservation.depositAmount.toLocaleString()}원 결제`}
      </button>
    </div>
  )
}
