package com.app.tably.reservation.service;

/**
 * 2막: 슬롯 선점 앞단의 동시성 게이트.
 *
 * 오픈런(S2)에서 실패할 다수(5,000명 중 4,760명)가 DB 행 락까지 내려가 커넥션을 점유하는 것이
 * 1막(DB 락 단독)의 병목 — 게이트는 그 실패를 DB 앞에서 즉시 돌려보내는 역할만 한다.
 * 정합성(중복 예약 0건)의 최종 보루는 여전히 DB 락 + 활성 예약 검증이며,
 * 게이트가 유실·오동작해도 느려질 뿐 중복은 생기지 않는다.
 */
public interface SlotHoldGate {

    /**
     * 슬롯 선점 시도 전 호출. false면 이미 다른 손님이 선점한 것 — 즉시 실패 응답.
     */
    boolean tryAcquire(Long slotId, Long memberId);

    /**
     * 선점 실패(트랜잭션 롤백)·취소 시 호출 — 슬롯을 다시 잡을 수 있게 게이트를 되돌린다.
     */
    void release(Long slotId);
}
