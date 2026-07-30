package com.app.tably.restaurant.dto;

import com.app.tably.restaurant.entity.ReservationPolicy;

public record PolicyResponseDto(
        Long id,
        Long restaurantId,
        int depositPerPerson,
        String refundRule,
        String openRule,
        int tablesPerTime,
        String slotTimes,
        String closedDays
) {

    public static PolicyResponseDto from(ReservationPolicy policy) {
        return new PolicyResponseDto(
                policy.getId(),
                policy.getRestaurant().getId(),
                policy.getDepositPerPerson(),
                policy.getRefundRule(),
                policy.getOpenRule(),
                policy.getTablesPerTime(),
                policy.getSlotTimes(),
                policy.getClosedDays()
        );
    }
}
