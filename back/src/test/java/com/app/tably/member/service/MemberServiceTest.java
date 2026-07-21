package com.app.tably.member.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.config.jwt.JwtTokenProvider;
import com.app.tably.member.dto.LoginRequestDto;
import com.app.tably.member.dto.SignupRequestDto;
import com.app.tably.member.dto.TokenResponseDto;
import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private MemberService memberService;

    private Member member(Long id) {
        return Member.builder()
                .id(id)
                .email("guest@tably.com")
                .password("encoded-password")
                .name("김지현")
                .role(Role.GUEST)
                .build();
    }

    @Test
    @DisplayName("회원가입 성공 시 비밀번호는 인코딩되어 저장된다")
    void signUp_success() {
        var request = new SignupRequestDto("guest@tably.com", "password123", "김지현", Role.GUEST);
        given(memberRepository.existsByEmail("guest@tably.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("encoded-password");
        given(memberRepository.save(any(Member.class))).willReturn(member(1L));

        Long memberId = memberService.signUp(request);

        assertThat(memberId).isEqualTo(1L);
        verify(passwordEncoder).encode("password123");
    }

    @Test
    @DisplayName("중복 이메일 가입은 DUPLICATE_EMAIL 예외")
    void signUp_duplicateEmail() {
        var request = new SignupRequestDto("guest@tably.com", "password123", "김지현", Role.GUEST);
        given(memberRepository.existsByEmail("guest@tably.com")).willReturn(true);

        assertThatThrownBy(() -> memberService.signUp(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("로그인 성공 시 Bearer 액세스 토큰을 반환한다")
    void login_success() {
        given(memberRepository.findByEmail("guest@tably.com")).willReturn(Optional.of(member(1L)));
        given(passwordEncoder.matches("password123", "encoded-password")).willReturn(true);
        given(jwtTokenProvider.createAccessToken(1L, Role.GUEST)).willReturn("access-token");

        TokenResponseDto token = memberService.login(new LoginRequestDto("guest@tably.com", "password123"));

        assertThat(token.accessToken()).isEqualTo("access-token");
        assertThat(token.tokenType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("없는 이메일과 잘못된 비밀번호는 동일하게 LOGIN_FAILED 예외")
    void login_fail() {
        given(memberRepository.findByEmail("none@tably.com")).willReturn(Optional.empty());
        assertThatThrownBy(() -> memberService.login(new LoginRequestDto("none@tably.com", "password123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);

        given(memberRepository.findByEmail("guest@tably.com")).willReturn(Optional.of(member(1L)));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);
        assertThatThrownBy(() -> memberService.login(new LoginRequestDto("guest@tably.com", "wrong")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("내 정보 조회 — 없는 회원이면 MEMBER_NOT_FOUND 예외")
    void getMyInfo() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L)));
        assertThat(memberService.getMyInfo(1L).email()).isEqualTo("guest@tably.com");

        given(memberRepository.findById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> memberService.getMyInfo(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }
}
