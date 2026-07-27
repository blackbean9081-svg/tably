package com.app.tably.reservation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppealStatus {

    OPEN("접수 — 운영자 검토 대기"),
    ACCEPTED("인용 — 노쇼 철회 + 환불"),
    REJECTED("기각");

    private final String description;
}
