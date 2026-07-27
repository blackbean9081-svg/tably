package com.app.tably.settlement.controller;

import com.app.tably.common.response.ApiResponse;
import com.app.tably.settlement.batch.SettlementScheduler;
import com.app.tably.settlement.dto.SettlementRunResultDto;
import com.app.tably.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * FR-17: 운영자의 정산 수동 실행 — 배치 실패 건 재처리·시연용 (Swagger).
 */
@RestController
@RequestMapping("/api/admin/settlements")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SettlementAdminController {

    private final SettlementScheduler settlementScheduler;

    @PostMapping("/run")
    public ApiResponse<SettlementRunResultDto> run(@RequestParam String month) {
        return ApiResponse.ok(settlementScheduler.runFor(SettlementService.parseMonth(month)));
    }
}
