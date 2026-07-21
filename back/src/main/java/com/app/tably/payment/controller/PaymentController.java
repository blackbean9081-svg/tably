package com.app.tably.payment.controller;

import com.app.tably.common.response.ApiResponse;
import com.app.tably.payment.dto.PaymentApproveRequestDto;
import com.app.tably.payment.dto.PaymentResponseDto;
import com.app.tably.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ApiResponse<PaymentResponseDto> approve(Authentication authentication,
                                                   @Valid @RequestBody PaymentApproveRequestDto request) {
        return ApiResponse.ok(paymentService.approve((Long) authentication.getPrincipal(), request));
    }

    @GetMapping("/reservations/{reservationId}")
    public ApiResponse<List<PaymentResponseDto>> getPayments(Authentication authentication,
                                                             @PathVariable Long reservationId) {
        return ApiResponse.ok(paymentService.getPayments(
                (Long) authentication.getPrincipal(), reservationId));
    }
}
