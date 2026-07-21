package com.app.tably.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {

    GUEST("게스트"),
    OWNER("사장");

    private final String Role;

}
