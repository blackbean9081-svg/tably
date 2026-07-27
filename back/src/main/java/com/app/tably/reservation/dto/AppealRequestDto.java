package com.app.tably.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppealRequestDto(

        @NotBlank
        @Size(max = 500)
        String reason
) {
}
