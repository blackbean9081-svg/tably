package com.app.tably.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationStatus {

    PENDING("기록됨 — 발송 대기"),
    SENT("발송 완료"),
    FAILED("발송 실패 — 재시도 대상 (FR-21)");

    private final String description;
}
