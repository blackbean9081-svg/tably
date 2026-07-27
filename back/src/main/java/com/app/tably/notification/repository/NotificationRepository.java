package com.app.tably.notification.repository;

import com.app.tably.notification.entity.Notification;
import com.app.tably.notification.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByEventId(String eventId);

    // 재발송 배치(FR-21) 대상 — 생성 직후(발송 진행 중일 수 있는) PENDING은 threshold로 거른다
    List<Notification> findAllByStatusInAndAttemptCountLessThanAndCreatedAtBefore(
            Collection<NotificationStatus> statuses, int maxAttempts, LocalDateTime threshold);
}
