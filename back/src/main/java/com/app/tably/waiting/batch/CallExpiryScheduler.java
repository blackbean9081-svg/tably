package com.app.tably.waiting.batch;

import com.app.tably.waiting.service.WaitingService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallExpiryScheduler {

    private final WaitingService waitingService;

    // FR-16: 호출 10분 미도착 자동 만료
    @Scheduled(fixedDelay = 60_000)
    public void expireOverdueCalls() {
        waitingService.expireOverdueCalls();
    }
}
