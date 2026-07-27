package com.app.tably.reservation.batch;

import com.app.tably.reservation.entity.ReservationStatus;
import com.app.tably.reservation.repository.ReservationRepository;
import com.app.tably.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NoShowScheduler {

    // 예약 시각 +30분 무도착 = 노쇼 (S5)
    public static final Duration NO_SHOW_GRACE = Duration.ofMinutes(30);

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    /**
     * FR-12: 노쇼 자동 전환 배치 (핵심영역 5).
     * 대상 id만 모아 건별 트랜잭션(markNoShow)으로 처리한다 —
     *  - 한 건의 실패가 나머지를 롤백시키지 않는다 (조건 6)
     *  - 재실행 시 이미 처리됐거나 방문 처리된 건은 조건부 UPDATE가 0건으로 걸러 멱등 (조건 3·4)
     */
    @Scheduled(fixedDelay = 60_000)
    public void processNoShows() {
        LocalDateTime threshold = LocalDateTime.now().minus(NO_SHOW_GRACE);
        List<Long> candidates = reservationRepository.findIdsByStatusAndSlotBefore(
                ReservationStatus.CONFIRMED, threshold.toLocalDate(), threshold.toLocalTime());
        if (candidates.isEmpty()) {
            return;
        }

        int converted = 0;
        int skipped = 0;
        int failed = 0;
        for (Long id : candidates) {
            try {
                if (reservationService.markNoShow(id)) {
                    converted++;
                } else {
                    skipped++;      // 배치가 읽은 뒤 방문 처리 등으로 상태가 바뀐 건 — 덮어쓰지 않는다
                }
            } catch (Exception e) {
                failed++;
                log.error("노쇼 전환 실패 — reservation {}", id, e);
            }
        }
        log.info("노쇼 배치: 대상 {}건, 전환 {}건, 경합 스킵 {}건, 실패 {}건",
                candidates.size(), converted, skipped, failed);
    }
}
