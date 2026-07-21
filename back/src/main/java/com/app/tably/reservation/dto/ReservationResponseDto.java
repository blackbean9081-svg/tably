package com.app.tably.reservation.dto;

import com.app.tably.reservation.entity.Reservation;
import com.app.tably.reservation.entity.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ReservationResponseDto(
        Long id,
        Long slotId,
        Long restaurantId,
        String restaurantName,
        LocalDate slotDate,
        LocalTime slotTime,
        int tableNo,
        int partySize,
        ReservationStatus status,
        LocalDateTime heldAt
) {

    public static ReservationResponseDto from(Reservation reservation) {
        var slot = reservation.getSlot();
        return new ReservationResponseDto(
                reservation.getId(),
                slot.getId(),
                slot.getRestaurant().getId(),
                slot.getRestaurant().getName(),
                slot.getSlotDate(),
                slot.getSlotTime(),
                slot.getTableNo(),
                reservation.getPartySize(),
                reservation.getStatus(),
                reservation.getHeldAt()
        );
    }
}
