package com.app.tably.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentApproveRequestDto(

        @NotNull
        Long reservationId,

        @Positive
        int amount,

        // 클라이언트가 결제 시도마다 생성하는 고유 키 — 재클릭·재전송 시 같은 키가 온다
        @NotBlank
        String idempotencyKey
) {
}
