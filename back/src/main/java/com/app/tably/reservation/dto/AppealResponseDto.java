package com.app.tably.reservation.dto;

import com.app.tably.reservation.entity.AppealStatus;
import com.app.tably.reservation.entity.NoShowAppeal;

import java.time.LocalDateTime;

public record AppealResponseDto(
        Long id,
        Long reservationId,
        String memberName,
        String restaurantName,
        String reason,
        AppealStatus status,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
) {

    public static AppealResponseDto from(NoShowAppeal appeal) {
        return new AppealResponseDto(
                appeal.getId(),
                appeal.getReservation().getId(),
                appeal.getMember().getName(),
                appeal.getReservation().getSlot().getRestaurant().getName(),
                appeal.getReason(),
                appeal.getStatus(),
                appeal.getCreatedAt(),
                appeal.getDecidedAt()
        );
    }
}
