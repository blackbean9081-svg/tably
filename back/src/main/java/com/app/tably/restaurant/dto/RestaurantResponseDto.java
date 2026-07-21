package com.app.tably.restaurant.dto;

import com.app.tably.restaurant.entity.Restaurant;

public record RestaurantResponseDto(
        Long id,
        String name,
        Long ownerId
) {

    public static RestaurantResponseDto from(Restaurant restaurant) {
        return new RestaurantResponseDto(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getOwner().getId()
        );
    }
}
