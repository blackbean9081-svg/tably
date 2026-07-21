package com.app.tably.member.dto;

import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;

public record MemberResponseDto(
        Long id,
        String email,
        String name,
        Role role
) {

    public static MemberResponseDto from(Member member) {
        return new MemberResponseDto(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getRole()
        );
    }
}
