package com.app.tably.waiting.controller;

import com.app.tably.common.response.ApiResponse;
import com.app.tably.waiting.dto.WaitingRegisterRequestDto;
import com.app.tably.waiting.dto.WaitingResponseDto;
import com.app.tably.waiting.service.WaitingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/waitings")
@RequiredArgsConstructor
public class WaitingController {

    private final WaitingService waitingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WaitingResponseDto> register(Authentication authentication,
                                                    @Valid @RequestBody WaitingRegisterRequestDto request) {
        return ApiResponse.ok(waitingService.register((Long) authentication.getPrincipal(), request));
    }

    @GetMapping("/{waitingId}")
    public ApiResponse<WaitingResponseDto> getMyWaiting(Authentication authentication,
                                                        @PathVariable Long waitingId) {
        return ApiResponse.ok(waitingService.getMyWaiting(
                (Long) authentication.getPrincipal(), waitingId));
    }

    @PostMapping("/restaurants/{restaurantId}/call")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<WaitingResponseDto> callNext(Authentication authentication,
                                                    @PathVariable Long restaurantId) {
        return ApiResponse.ok(waitingService.callNext(
                (Long) authentication.getPrincipal(), restaurantId));
    }

    @PostMapping("/{waitingId}/seat")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<WaitingResponseDto> seat(Authentication authentication,
                                                @PathVariable Long waitingId) {
        return ApiResponse.ok(waitingService.seat(
                (Long) authentication.getPrincipal(), waitingId));
    }

    @PostMapping("/{waitingId}/cancel")
    public ApiResponse<Void> cancel(Authentication authentication, @PathVariable Long waitingId) {
        waitingService.cancel((Long) authentication.getPrincipal(), waitingId);
        return ApiResponse.ok();
    }
}
