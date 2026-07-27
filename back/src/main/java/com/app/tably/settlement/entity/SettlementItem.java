package com.app.tably.settlement.entity;

import com.app.tably.reservation.entity.Reservation;
import jakarta.persistence.*;
import lombok.*;

/**
 * FR-18: 정산 건별 내역 — "왜 이 금액인지"의 근거.
 * (reservation, type) 유니크: 같은 노쇼를 두 번 몰수하거나 두 번 차감하는 것을 DB가 막는다.
 */
@Entity
@Table(name = "settlement_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_settlement_item_reservation_type",
                columnNames = {"reservation_id", "type"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SettlementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    private Settlement settlement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementItemType type;

    // FORFEIT: +몰수액 / REVOKE_ADJUSTMENT: -(이전 지급분)
    @Column(nullable = false)
    private int amount;
}
