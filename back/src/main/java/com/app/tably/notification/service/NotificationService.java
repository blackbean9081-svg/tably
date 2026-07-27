package com.app.tably.notification.service;

import com.app.tably.notification.entity.Notification;
import com.app.tably.notification.entity.NotificationStatus;
import com.app.tably.notification.event.NotificationEvent;
import com.app.tably.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * FR-20/21: 알림 2단계 처리.
 *  1) record — 발행 트랜잭션에 참여해 PENDING 기록. 도메인 변경이 롤백되면 알림도 함께 사라진다
 *  2) dispatchAfterCommit — 커밋 후 비동기 발송. 발송이 실패해도 기록이 남아 재시도 배치가 잇는다
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSender notificationSender;

    @EventListener
    public void record(NotificationEvent event) {
        notificationRepository.save(Notification.builder()
                .eventId(event.eventId())
                .memberId(event.memberId())
                .type(event.type())
                .message(event.message())
                .status(NotificationStatus.PENDING)
                .attemptCount(0)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // AFTER_COMMIT 시점엔 원 트랜잭션이 끝나 있으므로 상태 갱신용 새 트랜잭션이 필요하다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchAfterCommit(NotificationEvent event) {
        notificationRepository.findByEventId(event.eventId()).ifPresent(this::trySend);
    }

    /**
     * 재발송 배치 진입점 — 건별 트랜잭션.
     */
    @Transactional
    public boolean redeliver(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .map(this::trySend)
                .orElse(false);
    }

    private boolean trySend(Notification notification) {
        if (notification.getStatus() == NotificationStatus.SENT) {
            return true;    // 재시도 경합·배치 재실행 멱등
        }
        try {
            notificationSender.send(notification);
            notification.markSent(LocalDateTime.now());
            return true;
        } catch (Exception e) {
            notification.markFailed();
            log.warn("알림 발송 실패 — notification {} ({}회차): {}",
                    notification.getId(), notification.getAttemptCount(), e.getMessage());
            return false;
        }
    }
}
