package com.app.tably.settlement.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SettlementItemType {

    FORFEIT("노쇼 위약금 — 예약금 몰수분"),
    REVOKE_ADJUSTMENT("노쇼 철회 차감 — 이전 회차 지급분 마이너스 이월 (FR-19)");

    private final String description;
}
