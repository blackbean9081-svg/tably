package com.app.tably.restaurant.dto;

import java.util.List;

/**
 * S8: 휴업 처리는 "전부 성공 아니면 전부 실패"가 아니다 —
 * 건별 결과를 그대로 보고하고, 실패 건은 id로 남겨 재처리 근거로 쓴다.
 */
public record ClosureResultDto(
        int slotsClosed,
        int totalReservations,
        int canceled,
        List<Long> failedReservationIds
) {
}
