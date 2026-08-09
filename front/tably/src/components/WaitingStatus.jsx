import { useEffect, useRef, useState } from 'react'
import { getWaitingStatus } from '../api'

const POLL_INTERVAL_MS = 3000
const DONE_STATUSES = ['SEATED', 'EXPIRED', 'CANCELED']

const hhmmss = (iso) => (iso ? iso.slice(11, 19) : '')

const STATUS_BADGE = {
  WAITING: { label: '대기중', className: 'badge pending' },
  CALLED: { label: '호출됨', className: 'badge confirmed' },
  SEATED: { label: '입장 완료', className: 'badge done' },
  EXPIRED: { label: '만료', className: 'badge canceled' },
  CANCELED: { label: '취소', className: 'badge canceled' },
}

export default function WaitingStatus({ waiting, onUpdate, onRestart }) {
  const { id, status } = waiting
  const badge = STATUS_BADGE[status] ?? { label: status, className: 'badge done' }

  // 앞 팀 수가 줄어들 때 숫자에 팝 애니메이션을 준다
  const [pop, setPop] = useState(false)
  const prevAhead = useRef(waiting.aheadCount)
  useEffect(() => {
    if (waiting.aheadCount !== prevAhead.current) {
      prevAhead.current = waiting.aheadCount
      setPop(true)
      const t = setTimeout(() => setPop(false), 350)
      return () => clearTimeout(t)
    }
  }, [waiting.aheadCount])

  // 내 순번 3초 폴링 — 종료 상태(SEATED/EXPIRED/CANCELED)가 되면 멈춘다
  useEffect(() => {
    if (DONE_STATUSES.includes(status)) return
    const timer = setInterval(() => {
      getWaitingStatus(id).then(
        (fresh) => onUpdate(fresh),
        () => {
          // 일시적 폴링 실패는 다음 주기에서 회복한다
        },
      )
    }, POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [id, status, onUpdate])

  return (
    <div className="panel waiting-card">
      <h2>
        {waiting.restaurantName} 원격 줄서기 <span className={badge.className}>{badge.label}</span>
      </h2>
      <div className="waiting-no">
        <span className="label">대기번호</span>
        <span className="no">{waiting.waitingNo}</span>
      </div>

      {status === 'WAITING' && (
        <>
          <p className="ahead">
            내 앞에{' '}
            <strong className={pop ? 'pop' : ''}>{waiting.aheadCount ?? '-'}</strong>팀
          </p>
          <p className="live">
            <span className="pulse" />
            3초마다 자동 갱신 중
          </p>
        </>
      )}

      {status === 'CALLED' && (
        <div className="banner success called">
          <h2>지금 입장해주세요!</h2>
          <p>
            {hhmmss(waiting.calledAt)}에 호출되었습니다. 10분 안에 도착 확인이 없으면 순번이
            넘어갑니다.
          </p>
        </div>
      )}

      {status === 'EXPIRED' && (
        <>
          <div className="banner error">호출 후 10분이 지나 대기가 만료되었습니다.</div>
          <button className="primary" onClick={onRestart}>
            다시 웨이팅 등록
          </button>
        </>
      )}
      {status === 'SEATED' && <div className="banner success">입장 처리되었습니다. 맛있게 드세요!</div>}
      {status === 'CANCELED' && <div className="banner error">대기가 취소되었습니다.</div>}
    </div>
  )
}
