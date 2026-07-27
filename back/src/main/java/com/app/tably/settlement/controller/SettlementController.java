package com.app.tably.settlement.controller;

import com.app.tably.common.response.ApiResponse;
import com.app.tably.settlement.dto.SettlementResponseDto;
import com.app.tably.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    // FR-18: 사장의 정산서 확인 — 합계가 아니라 건별 내역 (내역 없는 정산은 CS를 부른다)
    @GetMapping("/{month}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<SettlementResponseDto> getStatement(Authentication authentication,
                                                           @PathVariable Long restaurantId,
                                                           @PathVariable String month) {
        return ApiResponse.ok(settlementService.getStatement(
                (Long) authentication.getPrincipal(), restaurantId, month));
    }
}
