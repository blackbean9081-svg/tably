package com.app.tably.member.controller;

import com.app.tably.common.response.ApiResponse;
import com.app.tably.member.dto.LoginRequestDto;
import com.app.tably.member.dto.MemberResponseDto;
import com.app.tably.member.dto.SignupRequestDto;
import com.app.tably.member.dto.TokenResponseDto;
import com.app.tably.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> signUp(@Valid @RequestBody SignupRequestDto request) {
        return ApiResponse.ok(memberService.signUp(request));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ApiResponse.ok(memberService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<MemberResponseDto> getMyInfo(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(memberService.getMyInfo(memberId));
    }
}
