package com.app.tably.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {

    GUEST("게스트"),
    OWNER("사장"),
    ADMIN("운영자");   // 플랫폼 CS/운영 — 이의신청 심사, 휴업 처리 (가입 경로 없음, 시드/수동 발급)

    private final String description;

}
