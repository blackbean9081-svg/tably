package com.app.tably.slot.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SlotStatus {

    OPEN("예약 가능 상태"),
    CLOSED("휴무·휴업 등으로 닫힌 상태");

    private final String description;
}
