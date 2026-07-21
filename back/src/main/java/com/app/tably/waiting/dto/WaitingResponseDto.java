package com.app.tably.waiting.dto;

import com.app.tably.waiting.entity.Waiting;
import com.app.tably.waiting.entity.WaitingStatus;

import java.time.LocalDateTime;

public record WaitingResponseDto(
        Long id,
        Long restaurantId,
        String restaurantName,
        int waitingNo,
        WaitingStatus status,
        LocalDateTime calledAt,
        Long aheadCount   // "내 앞에 N팀" — 목록 응답 등 계산이 불필요한 곳은 null
) {

    public static WaitingResponseDto of(Waiting waiting, Long aheadCount) {
        return new WaitingResponseDto(
                waiting.getId(),
                waiting.getRestaurant().getId(),
                waiting.getRestaurant().getName(),
                waiting.getWaitingNo(),
                waiting.getStatus(),
                waiting.getCalledAt(),
                aheadCount
        );
    }
}
