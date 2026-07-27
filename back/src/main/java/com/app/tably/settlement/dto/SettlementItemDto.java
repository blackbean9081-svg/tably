package com.app.tably.settlement.dto;

import com.app.tably.settlement.entity.SettlementItem;
import com.app.tably.settlement.entity.SettlementItemType;

import java.time.LocalDate;

public record SettlementItemDto(
        Long reservationId,
        LocalDate slotDate,
        SettlementItemType type,
        String description,
        int amount
) {

    public static SettlementItemDto from(SettlementItem item) {
        return new SettlementItemDto(
                item.getReservation().getId(),
                item.getReservation().getSlot().getSlotDate(),
                item.getType(),
                item.getType().getDescription(),
                item.getAmount()
        );
    }
}
