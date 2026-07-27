package com.app.tably.notification.service;

import com.app.tably.notification.entity.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LogNotificationSender implements NotificationSender {

    @Override
    public void send(Notification notification) {
        // 메시지에 [fail] 이 들어 있으면 발송 실패를 재현 — 재시도(FR-21) 경로 시연용
        if (notification.getMessage().contains("[fail]")) {
            throw new IllegalStateException("알림 발송 실패 (모의)");
        }
        log.info("[알림→member {}] ({}) {}",
                notification.getMemberId(), notification.getType().getDescription(), notification.getMessage());
    }
}
