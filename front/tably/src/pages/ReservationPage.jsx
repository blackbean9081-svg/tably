import { useEffect, useState } from 'react'
import { ApiError, RESTAURANT, getSlots, holdSlot } from '../api'
import SlotGrid from '../components/SlotGrid'
import PaymentPanel from '../components/PaymentPanel'
import ResultBanner from '../components/ResultBanner'

function defaultDate() {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export default function ReservationPage() {
  const [date, setDate] = useState(defaultDate)
  const [partySize, setPartySize] = useState(2)
  const [slotsData, setSlotsData] = useState(null) // { date, slots } — 마지막으로 조회한 결과
  const [reloadKey, setReloadKey] = useState(0) // 선점 실패·결제 후 재조회 트리거
  const [holding, setHolding] = useState(false)
  const [reservation, setReservation] = useState(null) // PENDING_PAYMENT 상태의 선점 건
  const [confirmation, setConfirmation] = useState(null) // CONFIRMED 결과
  const [banner, setBanner] = useState(null) // { type: 'info' | 'error', message }

  // 현재 선택한 날짜의 결과가 아직 없으면 로딩 중으로 본다
  const loading = !slotsData || slotsData.date !== date
  const slots = loading ? [] : slotsData.slots

  const refreshSlots = () => setReloadKey((k) => k + 1)

  useEffect(() => {
    let stale = false
    getSlots(RESTAURANT.id, date).then(
      (fresh) => {
        if (!stale) setSlotsData({ date, slots: fresh })
      },
      () => {
        if (!stale) setBanner({ type: 'error', message: '슬롯 조회에 실패했습니다.' })
      },
    )
    return () => {
      stale = true
    }
  }, [date, reloadKey])

  const handleSelect = async (slot) => {
    if (holding || loading) return
    setBanner(null)
    setConfirmation(null)
    setHolding(true)
    try {
      const held = await holdSlot(slot.slotId, partySize)
      setReservation(held)
      setBanner({ type: 'info', message: '슬롯을 선점했습니다. 10분 안에 예약금을 결제해주세요.' })
    } catch (e) {
      setBanner({
        type: 'error',
        message: e instanceof ApiError ? e.message : '요청에 실패했습니다.',
      })
      refreshSlots()
    } finally {
      setHolding(false)
    }
  }

  const handlePaid = (confirmed) => {
    setReservation(null)
    setConfirmation(confirmed)
    setBanner(null)
    refreshSlots()
  }

  const handlePayError = (e) => {
    setReservation(null)
    setBanner({
      type: 'error',
      message: e instanceof ApiError ? e.message : '결제에 실패했습니다.',
    })
    refreshSlots()
  }

  const handleExpire = () => {
    setReservation(null)
    setBanner({ type: 'error', message: '선점 시간이 만료되었습니다. 슬롯을 다시 선택해주세요.' })
    refreshSlots()
  }

  return (
    <section className="page">
      <div className="controls">
        <label>
          날짜
          <input
            type="date"
            value={date}
            disabled={!!reservation}
            onChange={(e) => {
              setDate(e.target.value)
              setConfirmation(null)
              setBanner(null)
            }}
          />
        </label>
        <label>
          인원
          <select
            value={partySize}
            disabled={!!reservation}
            onChange={(e) => setPartySize(Number(e.target.value))}
          >
            {[1, 2, 3, 4].map((n) => (
              <option key={n} value={n}>
                {n}명
              </option>
            ))}
          </select>
        </label>
        <span className="deposit-info">
          예약금 {(RESTAURANT.depositPerPerson * partySize).toLocaleString()}원 (1인{' '}
          {RESTAURANT.depositPerPerson.toLocaleString()}원)
        </span>
      </div>

      <ResultBanner
        banner={banner}
        confirmation={confirmation}
        onReset={() => setConfirmation(null)}
      />

      {reservation ? (
        <PaymentPanel
          reservation={reservation}
          onPaid={handlePaid}
          onError={handlePayError}
          onExpire={handleExpire}
        />
      ) : (
        <SlotGrid slots={slots} loading={loading || holding} onSelect={handleSelect} />
      )}
    </section>
  )
}
