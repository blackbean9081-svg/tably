import { useEffect, useState } from 'react'
import { bootstrap } from './api'
import ReservationPage from './pages/ReservationPage'
import WaitingPage from './pages/WaitingPage'
import './App.css'

function App() {
  const [tab, setTab] = useState('reservation')
  const [boot, setBoot] = useState(null) // { restaurant, policy }
  const [bootError, setBootError] = useState(null)

  // 앱 시작 시 자동 로그인(guest@tably.com) + 식당·정책 조회
  useEffect(() => {
    let stale = false
    bootstrap().then(
      (result) => {
        if (!stale) setBoot(result)
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
    <div className="app">
      <header className="app-header">
        <h1>tably</h1>
        <p className="restaurant">{boot ? boot.restaurant.name : ''}</p>
      </header>
      <nav className="tabs">
        <button
          className={tab === 'reservation' ? 'tab active' : 'tab'}
          onClick={() => setTab('reservation')}
        >
          예약
        </button>
        <button
          className={tab === 'waiting' ? 'tab active' : 'tab'}
          onClick={() => setTab('waiting')}
        >
          웨이팅
        </button>
      </nav>
      {bootError ? (
        <div className="banner error">
          {bootError}
          <br />
          백엔드(localhost:8080)가 최신 코드로 실행 중인지 확인해주세요.
        </div>
      ) : !boot ? (
        <p className="hint">로그인 중…</p>
      ) : (
        // 두 화면을 모두 마운트해두고 표시만 전환한다
        // (탭을 오가도 결제 카운트다운·웨이팅 폴링 상태가 유지되도록)
        <>
          <div style={{ display: tab === 'reservation' ? 'block' : 'none' }}>
            <ReservationPage restaurant={boot.restaurant} policy={boot.policy} />
          </div>
          <div style={{ display: tab === 'waiting' ? 'block' : 'none' }}>
            <WaitingPage restaurant={boot.restaurant} />
          </div>
        </>
      )}
    </div>
  )
}

export default App
