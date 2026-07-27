package com.app.tably.restaurant.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ClosureRequestDto(

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate
) {
}
