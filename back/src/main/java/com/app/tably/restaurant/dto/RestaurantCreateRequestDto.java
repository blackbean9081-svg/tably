package com.app.tably.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

public record RestaurantCreateRequestDto(

        @NotBlank
        String name
) {
}
