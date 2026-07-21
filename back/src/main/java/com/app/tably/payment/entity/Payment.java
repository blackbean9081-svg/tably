package com.app.tably.payment.entity;

import com.app.tably.reservation.entity.Reservation;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 결제 시도 1번 = payment 1행. 사건은 덮어쓰지 않고 쌓는다 (ERD 원칙 1).
 * 상태 변경(READY→APPROVED 등)만 같은 행에서 갱신하고, 재시도는 새 행이다.
 */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    // 멱등성(핵심영역 2)의 재료 — 같은 키 재요청은 새 청구 없이 기존 결과 반환
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "pg_tx_id")
    private String pgTxId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public void approve(String pgTxId) {
        this.status = PaymentStatus.APPROVED;
        this.pgTxId = pgTxId;
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    public void markUnknown() {
        this.status = PaymentStatus.UNKNOWN;
    }
}
