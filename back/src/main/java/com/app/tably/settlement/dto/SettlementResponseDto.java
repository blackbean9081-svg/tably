package com.app.tably.settlement.dto;

import com.app.tably.settlement.entity.Settlement;

import java.time.LocalDateTime;
import java.util.List;

public record SettlementResponseDto(
        Long id,
        Long restaurantId,
        String restaurantName,
        String settlementMonth,
        int totalForfeit,
        int totalCommission,
        int totalAdjustment,
        int payout,
        LocalDateTime createdAt,
        List<SettlementItemDto> items
) {

    public static SettlementResponseDto of(Settlement settlement, List<SettlementItemDto> items) {
        return new SettlementResponseDto(
                settlement.getId(),
                settlement.getRestaurant().getId(),
                settlement.getRestaurant().getName(),
                settlement.getSettlementMonth(),
                settlement.getTotalForfeit(),
                settlement.getTotalCommission(),
                settlement.getTotalAdjustment(),
                settlement.getPayout(),
                settlement.getCreatedAt(),
                items
        );
    }
}
