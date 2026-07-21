package com.app.tably.restaurant.controller;

import com.app.tably.common.response.ApiResponse;
import com.app.tably.restaurant.dto.PolicyRequestDto;
import com.app.tably.restaurant.dto.PolicyResponseDto;
import com.app.tably.restaurant.dto.RestaurantCreateRequestDto;
import com.app.tably.restaurant.dto.RestaurantResponseDto;
import com.app.tably.restaurant.service.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> create(Authentication authentication,
                                    @Valid @RequestBody RestaurantCreateRequestDto request) {
        return ApiResponse.ok(restaurantService.create((Long) authentication.getPrincipal(), request));
    }

    @GetMapping
    public ApiResponse<List<RestaurantResponseDto>> findAll() {
        return ApiResponse.ok(restaurantService.findAll());
    }

    @GetMapping("/{restaurantId}")
    public ApiResponse<RestaurantResponseDto> findOne(@PathVariable Long restaurantId) {
        return ApiResponse.ok(restaurantService.findOne(restaurantId));
    }

    @PutMapping("/{restaurantId}/policy")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<PolicyResponseDto> upsertPolicy(Authentication authentication,
                                                       @PathVariable Long restaurantId,
                                                       @Valid @RequestBody PolicyRequestDto request) {
        return ApiResponse.ok(restaurantService.upsertPolicy(
                (Long) authentication.getPrincipal(), restaurantId, request));
    }

    @GetMapping("/{restaurantId}/policy")
    public ApiResponse<PolicyResponseDto> findPolicy(@PathVariable Long restaurantId) {
        return ApiResponse.ok(restaurantService.findPolicy(restaurantId));
    }
}
