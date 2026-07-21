package com.app.tably.slot.controller;

import com.app.tably.common.response.ApiResponse;
import com.app.tably.slot.dto.SlotGenerateRequestDto;
import com.app.tably.slot.dto.SlotResponseDto;
import com.app.tably.slot.service.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    @PostMapping("/generate")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Integer> generate(Authentication authentication,
                                         @PathVariable Long restaurantId,
                                         @Valid @RequestBody SlotGenerateRequestDto request) {
        return ApiResponse.ok(slotService.generateMonthlySlots(
                (Long) authentication.getPrincipal(), restaurantId, request));
    }

    @GetMapping
    public ApiResponse<List<SlotResponseDto>> getSlots(@PathVariable Long restaurantId,
                                                       @RequestParam
                                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(slotService.getSlots(restaurantId, date));
    }
}
