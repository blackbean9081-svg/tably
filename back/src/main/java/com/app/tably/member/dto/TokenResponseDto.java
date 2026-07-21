package com.app.tably.member.dto;

public record TokenResponseDto(
        String accessToken,
        String tokenType
) {

    public static TokenResponseDto bearer(String accessToken) {
        return new TokenResponseDto(accessToken, "Bearer");
    }
}
