package com.app.tably.slot.batch;

import com.app.tably.slot.service.SlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SlotOpenScheduler {

    private final SlotService slotService;

    @Scheduled(fixedDelay = 60_000)
    public void openDueMonthlySlots() {
        slotService.openDueMonthlySlots(LocalDateTime.now());
    }
}
