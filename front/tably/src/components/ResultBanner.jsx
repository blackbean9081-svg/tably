import { Link } from 'react-router-dom'

const hhmm = (t) => t.slice(0, 5)

export default function ResultBanner({ banner, confirmation, onReset }) {
  if (confirmation) {
    return (
      <div className="banner success confirm-card">
        <h2>
          <span className="check-circle">✓</span>예약 확정
          <span className="badge confirmed">확정</span>
        </h2>
        <p>
          {confirmation.slotDate} {hhmm(confirmation.slotTime)} · 테이블 {confirmation.tableNo}번 ·{' '}
          {confirmation.partySize}명 · 예약금 {confirmation.depositAmount.toLocaleString()}원 결제
          완료
        </p>
        <div className="confirm-actions">
          <Link to="/my" className="banner-link">
            내 예약 보기
          </Link>
          <button onClick={onReset}>다른 슬롯 보기</button>
        </div>
      </div>
    )
  }
  if (!banner) return null
  return <div className={`banner ${banner.type}`}>{banner.message}</div>
}
