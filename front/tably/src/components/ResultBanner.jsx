export default function ResultBanner({ banner, confirmation, onReset }) {
  if (confirmation) {
    return (
      <div className="banner success">
        <h2>예약 확정</h2>
        <p>
          {confirmation.date} {confirmation.time} · 테이블 {confirmation.tableNo}번 ·{' '}
          {confirmation.partySize}명 · 예약금 {confirmation.depositAmount.toLocaleString()}원 결제
          완료
        </p>
        <button onClick={onReset}>다른 슬롯 보기</button>
      </div>
    )
  }
  if (!banner) return null
  return <div className={`banner ${banner.type}`}>{banner.message}</div>
}
