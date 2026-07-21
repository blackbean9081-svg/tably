package com.app.tably.waiting.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WaitingStatus {

    WAITING("대기 중"),
    CALLED("호출됨 — 10분 내 도착 확인 필요"),
    SEATED("입장 완료"),
    EXPIRED("호출 후 미도착으로 만료"),
    CANCELED("본인 취소");

    private final String description;
}
