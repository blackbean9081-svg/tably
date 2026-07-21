package com.app.tably.reservation.controller;

import com.app.tably.common.response.ApiResponse;
import com.app.tably.reservation.dto.ReservationHoldRequestDto;
import com.app.tably.reservation.dto.ReservationResponseDto;
import com.app.tably.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> hold(Authentication authentication,
                                  @Valid @RequestBody ReservationHoldRequestDto request) {
        return ApiResponse.ok(reservationService.hold((Long) authentication.getPrincipal(), request));
    }

    @GetMapping("/my")
    public ApiResponse<List<ReservationResponseDto>> getMyReservations(Authentication authentication) {
        return ApiResponse.ok(reservationService.getMyReservations((Long) authentication.getPrincipal()));
    }

    @GetMapping("/{reservationId}")
    public ApiResponse<ReservationResponseDto> getReservation(Authentication authentication,
                                                              @PathVariable Long reservationId) {
        return ApiResponse.ok(reservationService.getReservation(
                (Long) authentication.getPrincipal(), reservationId));
    }

    @PostMapping("/{reservationId}/cancel")
    public ApiResponse<Void> cancel(Authentication authentication, @PathVariable Long reservationId) {
        reservationService.cancelByUser((Long) authentication.getPrincipal(), reservationId);
        return ApiResponse.ok();
    }
}
