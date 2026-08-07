package com.app.tably.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record PolicyRequestDto(

        @PositiveOrZero
        int depositPerPerson,

        // "일수:환불율" 콤마 구분 (예: 7:100,3:50,1:0)
        @NotBlank
        @Pattern(regexp = "^\\d+:\\d{1,3}(,\\d+:\\d{1,3})*$", message = "형식: 일수:환불율,... (예: 7:100,3:50,1:0)")
        String refundRule,

        @NotBlank
        @Pattern(regexp = "^MONTHLY:([1-9]|[12]\\d|3[01]):([01]\\d|2[0-3]):[0-5]\\d$",
                message = "형식: MONTHLY:일:HH:mm (예: MONTHLY:1:10:00)")
        String openRule,

        @Positive
        int tablesPerTime,

        // "HH:mm" 콤마 구분 (예: 18:00,20:30)
        @NotBlank
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d(,([01]\\d|2[0-3]):[0-5]\\d)*$", message = "형식: HH:mm,HH:mm (예: 18:00,20:30)")
        String slotTimes,

        @Pattern(regexp = "^(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)(,(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY))*$",
                message = "형식: DayOfWeek 이름 콤마 구분 (예: MONDAY,TUESDAY)")
        String closedDays
) {
}
