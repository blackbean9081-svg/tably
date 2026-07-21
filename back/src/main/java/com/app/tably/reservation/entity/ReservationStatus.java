package com.app.tably.reservation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReservationStatus {

    PENDING_PAYMENT("슬롯 선점 후 결제 대기 (10분)"),
    EXPIRED("결제 시한 초과로 선점 해제"),
    CONFIRMED("예약금 결제 완료"),
    CANCELED_BY_USER("손님 취소 — 시점별 환불"),
    CANCELED_BY_SHOP("식당 취소 — 전액 환불"),
    VISITED("방문 완료 — 예약금 환불"),
    NO_SHOW("노쇼 — 예약금 몰수 (번복 가능 상태)"),
    NO_SHOW_REVOKED("노쇼 철회 — 환불 + 정산 차감");

    private final String description;
}
