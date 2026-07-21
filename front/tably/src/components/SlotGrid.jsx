export default function SlotGrid({ slots, loading, onSelect }) {
  if (loading && slots.length === 0) {
    return <p className="hint">슬롯을 불러오는 중…</p>
  }
  const times = [...new Set(slots.map((s) => s.time))]
  return (
    <div className={loading ? 'slot-groups dim' : 'slot-groups'}>
      {times.map((time) => (
        <div key={time} className="slot-group">
          <h2>{time}</h2>
          <div className="slot-grid">
            {slots
              .filter((s) => s.time === time)
              .map((slot) => (
                <button
                  key={slot.slotId}
                  className={`slot ${slot.status === 'AVAILABLE' ? 'available' : 'closed'}`}
                  onClick={() => onSelect(slot)}
                >
                  <span className="table">테이블 {slot.tableNo}</span>
                  <span className="state">
                    {slot.status === 'AVAILABLE' ? '예약 가능' : '마감'}
                  </span>
                </button>
              ))}
          </div>
        </div>
      ))}
    </div>
  )
}
