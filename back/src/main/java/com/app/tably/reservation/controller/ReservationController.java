package com.app.tably.reservation.controller;

import com.app.tably.common.response.ApiResponse;
import com.app.tably.reservation.dto.AppealRequestDto;
import com.app.tably.reservation.dto.AppealResponseDto;
import com.app.tably.reservation.dto.ReservationHoldRequestDto;
import com.app.tably.reservation.dto.ReservationResponseDto;
import com.app.tably.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

    // FR-11: 사장의 방문 완료 처리 + 노쇼 당일 정정 (S5)
    @PostMapping("/{reservationId}/visit")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<Void> markVisited(Authentication authentication, @PathVariable Long reservationId) {
        reservationService.markVisited((Long) authentication.getPrincipal(), reservationId);
        return ApiResponse.ok();
    }

    // FR-13: 노쇼 이의신청 접수 (손님)
    @PostMapping("/{reservationId}/no-show-appeal")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AppealResponseDto> fileNoShowAppeal(Authentication authentication,
                                                           @PathVariable Long reservationId,
                                                           @Valid @RequestBody AppealRequestDto request) {
        return ApiResponse.ok(reservationService.fileNoShowAppeal(
                (Long) authentication.getPrincipal(), reservationId, request));
    }
}
