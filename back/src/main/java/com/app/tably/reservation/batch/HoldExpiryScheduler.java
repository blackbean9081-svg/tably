package com.app.tably.reservation.batch;

import com.app.tably.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HoldExpiryScheduler {

    private final ReservationService reservationService;

    // FR-05: 10분 내 미결제 슬롯 자동 해제 — 1분 주기면 최대 지연 1분 (요구 수준 내)
    @Scheduled(fixedDelay = 60_000)
    public void expireOverdueHolds() {
        reservationService.expireOverdueHolds();
    }
}
