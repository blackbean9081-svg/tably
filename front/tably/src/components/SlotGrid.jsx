const hhmm = (t) => t.slice(0, 5) // "18:00:00" → "18:00"

export default function SlotGrid({ slots, loading, onSelect }) {
  if (loading && slots.length === 0) {
    return <p className="hint">슬롯을 불러오는 중…</p>
  }
  if (slots.length === 0) {
    return <p className="hint">이 날짜에는 예약 가능한 슬롯이 없습니다. 다른 날짜를 선택해주세요.</p>
  }
  const times = [...new Set(slots.map((s) => s.slotTime))]
  return (
    <div className={loading ? 'slot-groups dim' : 'slot-groups'}>
      <div className="slot-legend">
        <span>
          <span className="dot open" />
          예약 가능
        </span>
        <span>
          <span className="dot closed" />
          마감
        </span>
      </div>
      {times.map((time) => {
        const group = slots.filter((s) => s.slotTime === time)
        const openCount = group.filter((s) => s.status === 'OPEN').length
        return (
          <div key={time} className="slot-group">
            <h2>
              {hhmm(time)}
              <span className={openCount > 0 ? 'remaining' : 'remaining zero'}>
                {openCount > 0 ? `${openCount}자리 남음` : '마감'}
              </span>
            </h2>
            <div className="slot-grid">
              {group.map((slot) => (
                <button
                  key={slot.id}
                  className={`slot ${slot.status === 'OPEN' ? 'available' : 'closed'}`}
                  onClick={() => onSelect(slot)}
                >
                  <span className="table">테이블 {slot.tableNo}</span>
                  <span className="state">{slot.status === 'OPEN' ? '예약 가능' : '마감'}</span>
                </button>
              ))}
            </div>
          </div>
        )
      })}
    </div>
  )
}
