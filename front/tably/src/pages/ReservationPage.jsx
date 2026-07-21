import { useEffect, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { ApiError, getPolicy, getRestaurant, getSlots, holdSlot } from '../api'
import SlotGrid from '../components/SlotGrid'
import PaymentPanel from '../components/PaymentPanel'
import ResultBanner from '../components/ResultBanner'

// 시드 슬롯이 다음 달에 생성되므로 기본 날짜는 다음 달 15일
function defaultDate() {
  const d = new Date()
  d.setMonth(d.getMonth() + 1, 15)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

// 라우트 진입점: /restaurants/:id/reserve?date=YYYY-MM-DD
export default function ReservationPage() {
  const { id } = useParams()
  const restaurantId = Number(id)
  const [ctx, setCtx] = useState(null) // { restaurant, policy }
  const [error, setError] = useState(null)

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
  return <ReservationView restaurant={ctx.restaurant} policy={ctx.policy} />
}

function ReservationView({ restaurant, policy }) {
  const [searchParams] = useSearchParams()
  const [date, setDate] = useState(() => searchParams.get('date') ?? defaultDate())
  const [partySize, setPartySize] = useState(2)
  const [slotsData, setSlotsData] = useState(null) // { date, slots } — 마지막 조회 결과
  const [reloadKey, setReloadKey] = useState(0) // 선점 실패·결제 후 재조회 트리거
  const [holding, setHolding] = useState(false)
  const [reservation, setReservation] = useState(null) // PENDING_PAYMENT 상태의 선점 건
  const [sheetOpen, setSheetOpen] = useState(false) // 결제 시트 표시 여부
  const [confirmation, setConfirmation] = useState(null) // CONFIRMED 결과
  const [banner, setBanner] = useState(null) // { type: 'info' | 'error', message }

  // 현재 선택한 날짜의 결과가 아직 없으면 로딩 중으로 본다
  const loading = !slotsData || slotsData.date !== date
  const slots = loading ? [] : slotsData.slots
  const depositAmount = policy.depositPerPerson * partySize

  const refreshSlots = () => setReloadKey((k) => k + 1)

  useEffect(() => {
    let stale = false
    getSlots(restaurant.id, date).then(
      (fresh) => {
        if (!stale) setSlotsData({ date, slots: fresh })
      },
      (e) => {
        if (!stale) setBanner({ type: 'error', message: `슬롯 조회에 실패했습니다. (${e.message})` })
      },
    )
    return () => {
      stale = true
    }
  }, [restaurant.id, date, reloadKey])

  // 선점 만료 감시 — 결제 시트가 닫혀 있어도 10분 경과 시 자동 해제 처리
  useEffect(() => {
    if (!reservation) return
    const timer = setInterval(() => {
      if (Date.now() >= reservation.expiresAt) {
        setReservation(null)
        setSheetOpen(false)
        setBanner({ type: 'error', message: '선점 시간이 만료되었습니다. 슬롯을 다시 선택해주세요.' })
        setReloadKey((k) => k + 1)
      }
    }, 1000)
    return () => clearInterval(timer)
  }, [reservation])

  const handleSelect = async (slot) => {
    if (holding || loading) return
    if (reservation) {
      // 이미 선점한 슬롯이 있으면 결제 시트를 다시 연다
      setSheetOpen(true)
      return
    }
    setBanner(null)
    setConfirmation(null)
    setHolding(true)
    try {
      const held = await holdSlot(restaurant, slot, partySize, depositAmount)
      setReservation(held)
      setSheetOpen(true)
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
    setSheetOpen(false)
    setConfirmation(confirmed)
    setBanner(null)
    refreshSlots()
  }

  const handlePayError = (e) => {
    setReservation(null)
    setSheetOpen(false)
    setBanner({
      type: 'error',
      message: e instanceof ApiError ? e.message : '결제에 실패했습니다.',
    })
    refreshSlots()
  }

  return (
    <section className="page">
      <h2 className="section-title">{restaurant.name} 예약</h2>
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
        <div className="party-control">
          <span className="control-label">인원</span>
          <div className="segments">
            {[1, 2, 3, 4].map((n) => (
              <button
                key={n}
                className={partySize === n ? 'segment active' : 'segment'}
                disabled={!!reservation}
                onClick={() => setPartySize(n)}
              >
                {n}명
              </button>
            ))}
          </div>
        </div>
      </div>
      <p className="deposit-info">
        예약금 <strong>{depositAmount.toLocaleString()}원</strong> (1인{' '}
        {policy.depositPerPerson.toLocaleString()}원 × {partySize}명) · 정상 방문 시 전액 환불
      </p>

      <ResultBanner
        banner={banner}
        confirmation={confirmation}
        onReset={() => setConfirmation(null)}
      />

      <SlotGrid slots={slots} loading={loading || holding} onSelect={handleSelect} />

      {reservation && !sheetOpen && (
        <PendingBar reservation={reservation} onOpen={() => setSheetOpen(true)} />
      )}
      {reservation && sheetOpen && (
        <PaymentPanel
          reservation={reservation}
          onPaid={handlePaid}
          onError={handlePayError}
          onClose={() => setSheetOpen(false)}
        />
      )}
    </section>
  )
}

// 결제 시트를 닫아둔 동안 하단에 떠 있는 "선점중" 바
function PendingBar({ reservation, onOpen }) {
  const [remaining, setRemaining] = useState(() =>
    Math.max(0, reservation.expiresAt - Date.now()),
  )

  useEffect(() => {
    const timer = setInterval(
      () => setRemaining(Math.max(0, reservation.expiresAt - Date.now())),
      1000,
    )
    return () => clearInterval(timer)
  }, [reservation.expiresAt])

  const minutes = String(Math.floor(remaining / 60000)).padStart(2, '0')
  const seconds = String(Math.floor((remaining % 60000) / 1000)).padStart(2, '0')

  return (
    <button className="pending-bar" onClick={onOpen}>
      <span className="badge pending">선점중</span>
      <span className="pending-info">
        {reservation.slotTime.slice(0, 5)} · 테이블 {reservation.tableNo} · 결제하기
      </span>
      <strong className="pending-timer">
        {minutes}:{seconds}
      </strong>
    </button>
  )
}
