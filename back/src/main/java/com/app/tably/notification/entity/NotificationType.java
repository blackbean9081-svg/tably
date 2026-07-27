package com.app.tably.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// FR-20: 알림을 발행하는 도메인 이벤트 종류
@Getter
@RequiredArgsConstructor
public enum NotificationType {

    RESERVATION_CONFIRMED("예약 확정"),
    RESERVATION_CANCELED("예약 취소"),
    NO_SHOW_MARKED("노쇼 처리"),
    NO_SHOW_REVOKED("노쇼 철회"),
    WAITING_CALLED("웨이팅 호출");

    private final String description;
}
