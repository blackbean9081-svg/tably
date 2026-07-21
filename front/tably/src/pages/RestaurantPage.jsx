import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getPolicy, getRestaurant } from '../api'

// 1단계에서는 예약/웨이팅 진입만 제공한다.
// 2단계에서 정책 안내(예약금·환불 규정)와 날짜 선택으로 확장 예정.
export default function RestaurantPage() {
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

  const { restaurant, policy } = ctx
  return (
    <section className="page">
      <h2 className="section-title">{restaurant.name}</h2>
      <p className="hint">
        예약금 {policy.depositPerPerson.toLocaleString()}원/인 · 타임 {policy.slotTimes}
      </p>
      <div className="entry-buttons">
        <Link to={`/restaurants/${restaurantId}/reserve`} className="primary-link">
          예약하기
        </Link>
        <Link to={`/restaurants/${restaurantId}/waiting`} className="secondary-link">
          웨이팅 등록
        </Link>
      </div>
    </section>
  )
}
