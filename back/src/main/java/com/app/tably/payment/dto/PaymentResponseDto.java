package com.app.tably.payment.dto;

import com.app.tably.payment.entity.Payment;
import com.app.tably.payment.entity.PaymentStatus;
import com.app.tably.payment.entity.PaymentType;

import java.time.LocalDateTime;

public record PaymentResponseDto(
        Long id,
        Long reservationId,
        PaymentType type,
        int amount,
        PaymentStatus status,
        String idempotencyKey,
        String pgTxId,
        LocalDateTime createdAt
) {

    public static PaymentResponseDto from(Payment payment) {
        return new PaymentResponseDto(
                payment.getId(),
                payment.getReservation().getId(),
                payment.getType(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getIdempotencyKey(),
                payment.getPgTxId(),
                payment.getCreatedAt()
        );
    }
}
