import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ApiError, getRestaurant, registerWaiting } from '../api'
import WaitingStatus from '../components/WaitingStatus'

// 라우트 진입점: /restaurants/:id/waiting
export default function WaitingPage() {
  const { id } = useParams()
  const restaurantId = Number(id)
  const [restaurant, setRestaurant] = useState(null)
  const [loadError, setLoadError] = useState(null)

  useEffect(() => {
    let stale = false
    getRestaurant(restaurantId).then(
      (r) => {
        if (!stale) setRestaurant(r)
      },
      (e) => {
        if (!stale) setLoadError(e.message)
      },
    )
    return () => {
      stale = true
    }
  }, [restaurantId])

  if (loadError) return <div className="banner error">{loadError}</div>
  if (!restaurant) return <p className="hint">불러오는 중…</p>
  return <WaitingView restaurant={restaurant} />
}

function WaitingView({ restaurant }) {
  const [waiting, setWaiting] = useState(null) // WaitingResponseDto
  const [registering, setRegistering] = useState(false)
  const [error, setError] = useState(null)

  const handleRegister = async () => {
    if (registering) return
    setError(null)
    setRegistering(true)
    try {
      setWaiting(await registerWaiting(restaurant.id))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '웨이팅 등록에 실패했습니다.')
    } finally {
      setRegistering(false)
    }
  }

  return (
    <section className="page">
      <h2 className="section-title">{restaurant.name} 웨이팅</h2>
      {error && <div className="banner error">{error}</div>}
      {waiting ? (
        <WaitingStatus waiting={waiting} onUpdate={setWaiting} onRestart={() => setWaiting(null)} />
      ) : (
        <div className="panel">
          <h2>원격 줄서기</h2>
          <p className="waiting-desc">
            지금 매장 앞에 줄을 서지 않아도 됩니다. 등록하면 내 순번이 3초마다 갱신되고, 차례가
            되면 호출 안내가 표시됩니다. 호출 후 10분 안에 도착해주세요.
          </p>
          <button className="primary" onClick={handleRegister} disabled={registering}>
            {registering ? '등록 중…' : '웨이팅 등록'}
          </button>
        </div>
      )}
    </section>
  )
}
