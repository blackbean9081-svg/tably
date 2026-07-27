package com.app.tably.notification.batch;

import com.app.tably.notification.entity.Notification;
import com.app.tably.notification.entity.NotificationStatus;
import com.app.tably.notification.repository.NotificationRepository;
import com.app.tably.notification.service.NotificationService;
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
public class NotificationRetryScheduler {

    // 발송 시도 상한 — 초과분은 FAILED로 남아 수동 확인 대상 (실제 채널 연동 시 DLQ로 대체)
    public static final int MAX_ATTEMPTS = 5;

    // 방금 만들어져 비동기 발송이 진행 중일 수 있는 PENDING은 건드리지 않는다
    private static final Duration MIN_AGE = Duration.ofMinutes(1);

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    /**
     * FR-21: 발송 실패·유실 재시도. FAILED뿐 아니라 발송 프로세스가 죽어
     * PENDING에 머문 건도 줍는다 (기록과 발송을 분리한 이유).
     */
    @Scheduled(fixedDelay = 60_000)
    public void retryUndelivered() {
        List<Notification> targets = notificationRepository
                .findAllByStatusInAndAttemptCountLessThanAndCreatedAtBefore(
                        List.of(NotificationStatus.PENDING, NotificationStatus.FAILED),
                        MAX_ATTEMPTS, LocalDateTime.now().minus(MIN_AGE));
        if (targets.isEmpty()) {
            return;
        }
        int sent = 0;
        for (Notification notification : targets) {
            try {
                if (notificationService.redeliver(notification.getId())) {
                    sent++;
                }
            } catch (Exception e) {
                log.error("알림 재발송 실패 — notification {}", notification.getId(), e);
            }
        }
        log.info("알림 재발송 배치: 대상 {}건, 성공 {}건", targets.size(), sent);
    }
}
