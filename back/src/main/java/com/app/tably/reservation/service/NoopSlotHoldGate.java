package com.app.tably.reservation.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 1막(tably.slot-hold.mode=db, 기본값): 게이트 없이 모든 요청이 DB 락 경로로 내려간다.
 * k6 전후 비교의 기준선(baseline) 역할.
 */
@Component
@ConditionalOnProperty(name = "tably.slot-hold.mode", havingValue = "db", matchIfMissing = true)
public class NoopSlotHoldGate implements SlotHoldGate {

    @Override
    public boolean tryAcquire(Long slotId, Long memberId) {
        return true;
    }

    @Override
    public void release(Long slotId) {
    }
}
