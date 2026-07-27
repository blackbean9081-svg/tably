package com.app.tably.notification.event;

import com.app.tably.notification.entity.NotificationType;

import java.util.UUID;

/**
 * FR-20: 도메인 이벤트. 발행부는 알림 저장·발송 방식을 모른다 —
 * 트랜잭션 커밋과 함께 기록되고, 커밋 후 비동기로 발송된다.
 */
public record NotificationEvent(String eventId, Long memberId, NotificationType type, String message) {

    public static NotificationEvent of(Long memberId, NotificationType type, String message) {
        return new NotificationEvent(UUID.randomUUID().toString(), memberId, type, message);
    }
}
