package com.app.tably.slot.dto;

import com.app.tably.slot.entity.Slot;
import com.app.tably.slot.entity.SlotStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record SlotResponseDto(
        Long id,
        LocalDate slotDate,
        LocalTime slotTime,
        int tableNo,
        SlotStatus status
) {

    public static SlotResponseDto from(Slot slot) {
        return new SlotResponseDto(
                slot.getId(),
                slot.getSlotDate(),
                slot.getSlotTime(),
                slot.getTableNo(),
                slot.getStatus()
        );
    }
}
