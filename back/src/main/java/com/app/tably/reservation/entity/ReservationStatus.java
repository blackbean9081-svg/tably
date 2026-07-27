package com.app.tably.reservation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Set;

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

    // 상태 머신 (requirements.md 4장). 여기 없는 상태는 종결 — 어떤 전이도 불가.
    // NO_SHOW → VISITED 정정의 "당일 자정까지" 시간 조건은 정정 API 쪽 책임 (S5)
    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED = Map.of(
            PENDING_PAYMENT, Set.of(CONFIRMED, EXPIRED),
            CONFIRMED, Set.of(CANCELED_BY_USER, CANCELED_BY_SHOP, VISITED, NO_SHOW),
            NO_SHOW, Set.of(VISITED, NO_SHOW_REVOKED)
    );

    public boolean canTransitionTo(ReservationStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }
}
