import { useState } from 'react'
import { RESTAURANT } from './api'
import ReservationPage from './pages/ReservationPage'
import './App.css'

function App() {
  const [tab, setTab] = useState('reservation')

  return (
    <div className="app">
      <header className="app-header">
        <h1>tably</h1>
        <p className="restaurant">{RESTAURANT.name}</p>
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
      {tab === 'reservation' ? (
        <ReservationPage />
      ) : (
        <p className="hint">웨이팅 화면은 2단계에서 구현합니다.</p>
      )}
    </div>
  )
}

export default App
