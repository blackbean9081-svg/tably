package com.app.tably.settlement.entity;

import com.app.tably.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * S7/FR-17: 식당별 월 정산. (restaurant, settlement_month) 유니크 —
 * 배치가 중간에 죽어 재실행돼도 같은 달을 두 번 지급하지 않는다.
 * 정산서는 합계가 아니라 건별 내역(SettlementItem)의 집합이다 (FR-18).
 */
@Entity
@Table(name = "settlement", uniqueConstraints = {
        @UniqueConstraint(name = "uk_settlement_restaurant_month",
                columnNames = {"restaurant_id", "settlement_month"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    // "2026-08" — 정산 대상 월 (지급은 다음 달 1일 배치)
    @Column(name = "settlement_month", nullable = false, length = 7)
    private String settlementMonth;

    @Column(name = "total_forfeit", nullable = false)
    private int totalForfeit;

    @Column(name = "total_commission", nullable = false)
    private int totalCommission;

    // 이전 회차 지급분의 노쇼 철회 차감 합 (0 이하)
    @Column(name = "total_adjustment", nullable = false)
    private int totalAdjustment;

    // 몰수 합 - 수수료 + 차감. 음수면 다음 회차에서 자연 상계된다
    @Column(name = "payout", nullable = false)
    private int payout;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
