import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ApiError,
  cancelReservation,
  getMyReservations,
  getPolicy,
  getRefundPreview,
} from '../api'
import { daysUntil, refundAmountFor, refundPercentFor } from '../lib/refund'

const hhmm = (t) => t.slice(0, 5)

const STATUS_BADGE = {
  PENDING_PAYMENT: { label: '선점중', className: 'badge pending' },
  CONFIRMED: { label: '확정', className: 'badge confirmed' },
  CANCELED_BY_USER: { label: '취소', className: 'badge canceled' },
  CANCELED_BY_SHOP: { label: '식당 취소', className: 'badge canceled' },
  EXPIRED: { label: '만료', className: 'badge done' },
  VISITED: { label: '방문 완료', className: 'badge done' },
  NO_SHOW: { label: '노쇼', className: 'badge canceled' },
  NO_SHOW_REVOKED: { label: '노쇼 철회', className: 'badge done' },
}

const CANCELABLE = ['PENDING_PAYMENT', 'CONFIRMED']

export default function MyReservationsPage() {
  const [items, setItems] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [banner, setBanner] = useState(null) // { type, message }
  // { reservation, paid, daysLeft, percent, refund } — 취소 확인 시트
  const [cancelTarget, setCancelTarget] = useState(null)
  const [canceling, setCanceling] = useState(false)

  useEffect(() => {
    let stale = false
    getMyReservations().then(
      (list) => {
        if (!stale) setItems(list)
      },
      (e) => {
        if (!stale) setBanner({ type: 'error', message: e.message })
      },
    )
    return () => {
      stale = true
    }
  }, [reloadKey])

  // 취소 버튼 → 환불액 사전 고지 시트를 연다.
  // 실제 예약은 서버 계산(refund-preview) — 고지액과 실제 환불액의 계산 경로를 하나로 맞춘다.
  // 목 예약(id 음수)은 백엔드에 없으므로 기존 클라이언트 계산을 유지한다.
  const openCancel = async (reservation) => {
    setBanner(null)
    try {
      if (reservation.id > 0) {
        const preview = await getRefundPreview(reservation.id)
        setCancelTarget({
          reservation,
          paid: preview.paidAmount,
          daysLeft: preview.daysLeft,
          percent: preview.refundRate,
          refund: preview.refundAmount,
        })
        return
      }
      const policy = await getPolicy(reservation.restaurantId)
      const paid = reservation.status === 'CONFIRMED' ? reservation.depositAmount ?? 0 : 0
      const daysLeft = daysUntil(reservation.slotDate)
      setCancelTarget({
        reservation,
        paid,
        daysLeft,
        percent: refundPercentFor(policy.refundRule, daysLeft),
        refund: refundAmountFor(policy.refundRule, daysLeft, paid),
      })
    } catch (e) {
      setBanner({ type: 'error', message: e.message })
    }
  }

  const confirmCancel = async () => {
    if (canceling) return
    setCanceling(true)
    const { reservation, paid, refund } = cancelTarget
    try {
      await cancelReservation(reservation.id)
      setBanner({
        type: 'info',
        message:
          reservation.status === 'CONFIRMED'
            ? `예약이 취소되었습니다. 환불 예정액 ${refund.toLocaleString()}원 (결제 ${paid.toLocaleString()}원)`
            : '선점이 취소되었습니다.',
      })
      setCancelTarget(null)
      setReloadKey((k) => k + 1)
    } catch (e) {
      setBanner({
        type: 'error',
        message: e instanceof ApiError ? e.message : '취소에 실패했습니다.',
      })
      setCancelTarget(null)
    } finally {
      setCanceling(false)
    }
  }

  if (!items && !banner) return <p className="hint">내 예약을 불러오는 중…</p>

  return (
    <section className="page">
      <h2 className="section-title">내 예약</h2>
      {banner && <div className={`banner ${banner.type}`}>{banner.message}</div>}

      {items && items.length === 0 && (
        <div className="panel empty-state">
          <p>아직 예약이 없습니다.</p>
          <Link to="/" className="primary-link">
            식당 보러 가기
          </Link>
        </div>
      )}

      <div className="reservation-list">
        {(items ?? []).map((r) => {
          const badge = STATUS_BADGE[r.status] ?? { label: r.status, className: 'badge done' }
          return (
            <div key={r.id} className="reservation-card">
              <div className="reservation-head">
                <span className="name">{r.restaurantName}</span>
                <span className={badge.className}>{badge.label}</span>
              </div>
              <p className="reservation-info">
                {r.slotDate} {hhmm(r.slotTime)} · 테이블 {r.tableNo} · {r.partySize}명
                {r.depositAmount != null && ` · 예약금 ${r.depositAmount.toLocaleString()}원`}
              </p>
              {CANCELABLE.includes(r.status) && (
                <button className="cancel-btn" onClick={() => openCancel(r)}>
                  예약 취소
                </button>
              )}
            </div>
          )
        })}
      </div>

      {items && items.some((r) => r.id < 0) && (
        <p className="hint">
          ※ 목 선점·결제로 만든 예약은 브라우저 새로고침 시 사라집니다 (백엔드 구현 후 실제
          데이터로 유지됩니다).
        </p>
      )}

      {cancelTarget && (
        <>
          <div className="sheet-backdrop" onClick={canceling ? undefined : () => setCancelTarget(null)} />
          <div className="sheet" role="dialog" aria-label="예약 취소 확인">
            <div className="sheet-handle" />
            <div className="sheet-head">
              <h2>예약을 취소할까요?</h2>
            </div>
            <dl className="detail">
              <div>
                <dt>예약</dt>
                <dd>
                  {cancelTarget.reservation.slotDate} {hhmm(cancelTarget.reservation.slotTime)} ·{' '}
                  {cancelTarget.reservation.restaurantName}
                </dd>
              </div>
              {cancelTarget.reservation.status === 'CONFIRMED' ? (
                <>
                  <div>
                    <dt>결제액</dt>
                    <dd>{cancelTarget.paid.toLocaleString()}원</dd>
                  </div>
                  <div>
                    <dt>환불율</dt>
                    <dd>
                      방문 {cancelTarget.daysLeft}일 전 기준 {cancelTarget.percent}%
                    </dd>
                  </div>
                  <div>
                    <dt>환불액</dt>
                    <dd className={cancelTarget.refund === 0 ? 'refund zero' : 'refund'}>
                      {cancelTarget.refund.toLocaleString()}원
                    </dd>
                  </div>
                </>
              ) : (
                <div>
                  <dt>안내</dt>
                  <dd>결제 전 선점 건입니다. 취소 시 슬롯이 즉시 풀립니다.</dd>
                </div>
              )}
            </dl>
            {cancelTarget.reservation.status === 'CONFIRMED' && cancelTarget.refund === 0 && (
              <div className="banner error">
                지금 취소하면 <strong>환불액이 0원</strong>입니다. 환불 규정을 확인해주세요.
              </div>
            )}
            <button className="primary danger" onClick={confirmCancel} disabled={canceling}>
              {canceling ? '취소 처리 중…' : '취소 확정'}
            </button>
            <button className="ghost" onClick={() => setCancelTarget(null)} disabled={canceling}>
              돌아가기
            </button>
          </div>
        </>
      )}
    </section>
  )
}
