package com.app.tably.member.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.config.jwt.JwtTokenProvider;
import com.app.tably.member.dto.LoginRequestDto;
import com.app.tably.member.dto.MemberResponseDto;
import com.app.tably.member.dto.SignupRequestDto;
import com.app.tably.member.dto.TokenResponseDto;
import com.app.tably.member.entity.Member;
import com.app.tably.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public Long signUp(SignupRequestDto request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        Member member = Member.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .role(request.role())
                .build();
        return memberRepository.save(member).getId();
    }

    public TokenResponseDto login(LoginRequestDto request) {
        // 이메일 미존재와 비밀번호 불일치를 같은 메시지로 응답 — 계정 존재 여부 노출 방지
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return TokenResponseDto.bearer(jwtTokenProvider.createAccessToken(member.getId(), member.getRole()));
    }

    public MemberResponseDto getMyInfo(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return MemberResponseDto.from(member);
    }
}
