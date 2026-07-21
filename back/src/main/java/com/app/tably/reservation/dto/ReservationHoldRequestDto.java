package com.app.tably.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReservationHoldRequestDto(

        @NotNull
        Long slotId,

        @Positive
        @Max(20)
        int partySize
) {
}
