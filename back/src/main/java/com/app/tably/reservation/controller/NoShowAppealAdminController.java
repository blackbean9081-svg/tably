package com.app.tably.reservation.controller;

import com.app.tably.common.response.ApiResponse;
import com.app.tably.reservation.dto.AppealResponseDto;
import com.app.tably.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FR-13: 운영자의 이의신청 심사 — v1은 화면 없이 Swagger로 시연한다.
 */
@RestController
@RequestMapping("/api/admin/appeals")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class NoShowAppealAdminController {

    private final ReservationService reservationService;

    @GetMapping
    public ApiResponse<List<AppealResponseDto>> getOpenAppeals() {
        return ApiResponse.ok(reservationService.getOpenAppeals());
    }

    // 인용 — 노쇼 철회(NO_SHOW_REVOKED) + 예약금 환불 + (P2) 정산 차감
    @PostMapping("/{appealId}/accept")
    public ApiResponse<AppealResponseDto> accept(@PathVariable Long appealId) {
        return ApiResponse.ok(reservationService.acceptAppeal(appealId));
    }

    @PostMapping("/{appealId}/reject")
    public ApiResponse<AppealResponseDto> reject(@PathVariable Long appealId) {
        return ApiResponse.ok(reservationService.rejectAppeal(appealId));
    }
}
