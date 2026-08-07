package com.app.tably.reservation.dto;

import com.app.tably.reservation.entity.ReservationStatus;

import java.time.LocalDate;

/**
 * S4: 취소 전 환불액 사전 고지 — "지금 취소하면 환불액 N원"의 서버 계산 결과.
 * 고지 없는 취소는 CS 분쟁의 근원이므로, 클라이언트 자체 계산이 아니라 이 응답을 근거로 고지한다.
 */
public record RefundPreviewResponseDto(
        Long reservationId,
        ReservationStatus status,
        boolean cancelable,
        int paidAmount,
        int refundRate,
        int refundAmount,
        long daysLeft,
        LocalDate visitDate,
        String refundRule
) {
}
