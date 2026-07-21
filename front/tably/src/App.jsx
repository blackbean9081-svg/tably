import { useEffect, useState } from 'react'
import { BrowserRouter, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { ensureLogin } from './api'
import HomePage from './pages/HomePage'
import RestaurantPage from './pages/RestaurantPage'
import ReservationPage from './pages/ReservationPage'
import WaitingPage from './pages/WaitingPage'
import MyReservationsPage from './pages/MyReservationsPage'
import './App.css'

function Layout({ children }) {
  const location = useLocation()
  const navigate = useNavigate()
  const isHome = location.pathname === '/'

  return (
    <div className="app">
      <header className="app-header">
        {!isHome && (
          <button className="back-btn" onClick={() => navigate(-1)} aria-label="뒤로">
            ←
          </button>
        )}
        <h1>tably</h1>
        {isHome && <p className="tagline">예약금으로 노쇼 없는 예약 — 오픈런과 웨이팅을 한 곳에서</p>}
      </header>
      <main className="app-main">{children}</main>
      <nav className="bottom-nav">
        <NavLink to="/" end className={({ isActive }) => (isActive ? 'nav-item active' : 'nav-item')}>
          <span className="icon">⌂</span>홈
        </NavLink>
        <NavLink
          to="/my"
          className={({ isActive }) => (isActive ? 'nav-item active' : 'nav-item')}
        >
          <span className="icon">☰</span>내 예약
        </NavLink>
      </nav>
    </div>
  )
}

function App() {
  const [ready, setReady] = useState(false)
  const [bootError, setBootError] = useState(null)

  // 앱 시작 시 시드 계정 자동 로그인 (로그인 화면 없음)
  useEffect(() => {
    let stale = false
    ensureLogin().then(
      () => {
        if (!stale) setReady(true)
      },
      (e) => {
        if (!stale) setBootError(e.message)
      },
    )
    return () => {
      stale = true
    }
  }, [])

  return (
    <BrowserRouter>
      <Layout>
        {bootError ? (
          <div className="banner error">
            {bootError}
            <br />
            백엔드(localhost:8080)가 실행 중인지 확인해주세요.
          </div>
        ) : !ready ? (
          <p className="hint">로그인 중…</p>
        ) : (
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/restaurants/:id" element={<RestaurantPage />} />
            <Route path="/restaurants/:id/reserve" element={<ReservationPage />} />
            <Route path="/restaurants/:id/waiting" element={<WaitingPage />} />
            <Route path="/my" element={<MyReservationsPage />} />
          </Routes>
        )}
      </Layout>
    </BrowserRouter>
  )
}

export default App
