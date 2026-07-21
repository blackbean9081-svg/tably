package com.app.tably.waiting.dto;

import jakarta.validation.constraints.NotNull;

public record WaitingRegisterRequestDto(

        @NotNull
        Long restaurantId
) {
}
