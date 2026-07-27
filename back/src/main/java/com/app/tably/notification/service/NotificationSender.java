package com.app.tably.notification.service;

import com.app.tably.notification.entity.Notification;

// 발송 채널 경계 — v1은 로그, 실제 채널(알림톡/푸시)은 사업자 계약 후 이 구현만 교체한다
public interface NotificationSender {

    void send(Notification notification);
}
