package com.app.tably.payment.repository;

import com.app.tably.payment.entity.Payment;
import com.app.tably.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findAllByReservationIdOrderByIdDesc(Long reservationId);

    // FR-07: UNKNOWN 재확인 / FR-09: 환불 실패 재처리 배치의 조회 대상
    List<Payment> findAllByStatus(PaymentStatus status);
}
