package com.app.tably.payment.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {

    READY("결제 시도 생성"),
    APPROVED("PG 승인 완료"),
    CANCELED("전액 취소"),
    PARTIAL_CANCELED("부분 취소"),
    FAILED("실패 확정"),
    UNKNOWN("PG 응답 미수신 — 재확인 대상 (S3)");

    private final String description;
}
