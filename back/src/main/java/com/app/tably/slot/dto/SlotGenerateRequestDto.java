package com.app.tably.slot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SlotGenerateRequestDto(

        // 생성 대상 월, 예: "2026-08"
        @NotBlank
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "형식: yyyy-MM (예: 2026-08)")
        String yearMonth
) {
}
