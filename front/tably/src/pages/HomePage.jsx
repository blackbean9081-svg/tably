import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getPolicy, getRestaurants } from '../api'

export default function HomePage() {
  const [cards, setCards] = useState(null) // [{ restaurant, policy }]
  const [error, setError] = useState(null)

  useEffect(() => {
    let stale = false
    getRestaurants()
      .then((restaurants) =>
        Promise.all(
          restaurants.map(async (restaurant) => ({
            restaurant,
            policy: await getPolicy(restaurant.id),
          })),
        ),
      )
      .then(
        (result) => {
          if (!stale) setCards(result)
        },
        (e) => {
          if (!stale) setError(e.message)
        },
      )
    return () => {
      stale = true
    }
  }, [])

  if (error) return <div className="banner error">{error}</div>
  if (!cards) return <p className="hint">식당을 불러오는 중…</p>

  return (
    <section className="page">
      <h2 className="section-title">예약 가능한 식당</h2>
      <div className="restaurant-list">
        {cards.map(({ restaurant, policy }) => (
          <Link key={restaurant.id} to={`/restaurants/${restaurant.id}`} className="restaurant-card">
            <div className="restaurant-card-head">
              <span className="name">{restaurant.name}</span>
              <span className="deposit-badge">
                예약금 {policy.depositPerPerson.toLocaleString()}원/인
              </span>
            </div>
            <div className="time-chips">
              {policy.slotTimes.split(',').map((t) => (
                <span key={t} className="chip">
                  {t.trim()}
                </span>
              ))}
              <span className="chip muted">테이블 {policy.tablesPerTime}개/타임</span>
            </div>
          </Link>
        ))}
      </div>
    </section>
  )
}
