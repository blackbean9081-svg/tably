package com.app.tably.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * FR-21: 알림 기록. 도메인 변경과 같은 트랜잭션에서 PENDING으로 저장되고,
 * 커밋 후 비동기 발송이 SENT/FAILED로 갱신한다 — 발송이 죽어도 기록은 남아 재시도된다.
 * 수신자는 id로만 참조한다 — 알림은 모든 도메인이 발행하므로 특정 도메인 엔티티에 결합하지 않는다.
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 발행 이벤트와 발송 시도를 잇는 키 — 커밋 후 비동기 핸들러가 이 값으로 행을 찾는다
    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public void markSent(LocalDateTime now) {
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
        this.attemptCount++;
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
        this.attemptCount++;
    }
}
