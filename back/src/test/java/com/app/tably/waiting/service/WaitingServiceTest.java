package com.app.tably.waiting.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.RestaurantRepository;
import com.app.tably.waiting.dto.WaitingRegisterRequestDto;
import com.app.tably.waiting.entity.Waiting;
import com.app.tably.waiting.entity.WaitingCounter;
import com.app.tably.waiting.entity.WaitingStatus;
import com.app.tably.waiting.repository.WaitingCounterRepository;
import com.app.tably.waiting.repository.WaitingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WaitingServiceTest {

    @Mock
    private WaitingRepository waitingRepository;

    @Mock
    private WaitingCounterRepository waitingCounterRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private WaitingService waitingService;

    private Member guest(Long id) {
        return Member.builder().id(id).email("guest@tably.com").password("pw").name("김지현").role(Role.GUEST).build();
    }

    private Restaurant restaurant(Long ownerId) {
        Member owner = Member.builder().id(ownerId).email("owner@tably.com").password("pw").name("박성호").role(Role.OWNER).build();
        return Restaurant.builder().id(10L).owner(owner).name("스시 준").build();
    }

    private Waiting waiting(Long id, Long memberId, int no, WaitingStatus status) {
        return Waiting.builder().id(id).restaurant(restaurant(99L)).member(guest(memberId))
                .waitingNo(no).status(status).build();
    }

    @Test
    @DisplayName("등록 — 카운터가 발급한 번호로 WAITING 행이 저장된다 (핵심영역 6)")
    void register_success() {
        WaitingCounter counter = new WaitingCounter(10L);
        counter.issueNext();    // 이미 1번이 발급된 상태 → 이번 등록은 2번
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant(99L)));
        given(memberRepository.findById(1L)).willReturn(Optional.of(guest(1L)));
        given(waitingRepository.existsByRestaurantIdAndMemberIdAndStatusIn(anyLong(), anyLong(), anyCollection()))
                .willReturn(false);
        given(waitingCounterRepository.findWithLockByRestaurantId(10L)).willReturn(Optional.of(counter));
        given(waitingRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(waitingRepository.countByRestaurantIdAndStatusAndWaitingNoLessThan(10L, WaitingStatus.WAITING, 2))
                .willReturn(1L);

        var result = waitingService.register(1L, new WaitingRegisterRequestDto(10L));

        assertThat(result.waitingNo()).isEqualTo(2);
        assertThat(result.status()).isEqualTo(WaitingStatus.WAITING);
        assertThat(result.aheadCount()).isEqualTo(1L);
        assertThat(counter.getLastNo()).isEqualTo(2);
        then(waitingCounterRepository).should().insertIfAbsent(10L);
    }

    @Test
    @DisplayName("이미 활성 웨이팅이 있으면 ALREADY_WAITING — 번호 발급 시도 전에 걸러진다")
    void register_alreadyWaiting() {
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant(99L)));
        given(memberRepository.findById(1L)).willReturn(Optional.of(guest(1L)));
        given(waitingRepository.existsByRestaurantIdAndMemberIdAndStatusIn(anyLong(), anyLong(), anyCollection()))
                .willReturn(true);

        assertThatThrownBy(() -> waitingService.register(1L, new WaitingRegisterRequestDto(10L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_WAITING);
        then(waitingCounterRepository).should(never()).insertIfAbsent(anyLong());
    }

    @Test
    @DisplayName("순번 조회 — 내 앞의 WAITING 수를 반환한다")
    void getMyWaiting() {
        given(waitingRepository.findById(5L)).willReturn(Optional.of(waiting(5L, 1L, 12, WaitingStatus.WAITING)));
        given(waitingRepository.countByRestaurantIdAndStatusAndWaitingNoLessThan(10L, WaitingStatus.WAITING, 12))
                .willReturn(7L);

        var result = waitingService.getMyWaiting(1L, 5L);

        assertThat(result.aheadCount()).isEqualTo(7L);
        assertThat(result.waitingNo()).isEqualTo(12);
    }

    @Test
    @DisplayName("다음 팀 호출 — WAITING 최소 번호가 CALLED로 바뀐다")
    void callNext() {
        Waiting next = waiting(5L, 1L, 3, WaitingStatus.WAITING);
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant(99L)));
        given(waitingRepository.findFirstByRestaurantIdAndStatusOrderByWaitingNoAsc(10L, WaitingStatus.WAITING))
                .willReturn(Optional.of(next));

        var result = waitingService.callNext(99L, 10L);

        assertThat(result.status()).isEqualTo(WaitingStatus.CALLED);
        assertThat(next.getCalledAt()).isNotNull();
    }

    @Test
    @DisplayName("호출 후 10분 지난 CALLED는 조건부 일괄 UPDATE로 EXPIRED — 도착 확인된 행은 덮지 않는다")
    void expireOverdueCalls() {
        given(waitingRepository.updateStatusAllCalledBefore(eq(WaitingStatus.CALLED), any(), eq(WaitingStatus.EXPIRED)))
                .willReturn(2);

        int count = waitingService.expireOverdueCalls();

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("도착 확인 — CALLED가 조건부 UPDATE로 SEATED가 된다")
    void seat_success() {
        given(waitingRepository.findById(5L)).willReturn(
                Optional.of(waiting(5L, 1L, 3, WaitingStatus.CALLED)),
                Optional.of(waiting(5L, 1L, 3, WaitingStatus.SEATED)));
        given(waitingRepository.updateStatusIfCurrentIn(5L, List.of(WaitingStatus.CALLED), WaitingStatus.SEATED))
                .willReturn(1);

        var result = waitingService.seat(99L, 5L);

        assertThat(result.status()).isEqualTo(WaitingStatus.SEATED);
    }

    @Test
    @DisplayName("만료 배치에 진 도착 확인은 WAITING_NOT_CALLED — 0건 갱신이면 경합 패배")
    void seat_lostRaceToExpiry() {
        given(waitingRepository.findById(5L)).willReturn(Optional.of(waiting(5L, 1L, 3, WaitingStatus.CALLED)));
        given(waitingRepository.updateStatusIfCurrentIn(anyLong(), anyCollection(), any())).willReturn(0);

        assertThatThrownBy(() -> waitingService.seat(99L, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WAITING_NOT_CALLED);
    }

    @Test
    @DisplayName("남의 식당 웨이팅 도착 확인은 NOT_RESTAURANT_OWNER")
    void seat_notOwner() {
        given(waitingRepository.findById(5L)).willReturn(Optional.of(waiting(5L, 1L, 3, WaitingStatus.CALLED)));

        assertThatThrownBy(() -> waitingService.seat(7L, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_RESTAURANT_OWNER);
        then(waitingRepository).should(never()).updateStatusIfCurrentIn(anyLong(), anyCollection(), any());
    }

    @Test
    @DisplayName("본인 취소 — WAITING/CALLED에서 CANCELED로")
    void cancel_success() {
        given(waitingRepository.findById(5L)).willReturn(Optional.of(waiting(5L, 1L, 3, WaitingStatus.WAITING)));
        given(waitingRepository.updateStatusIfCurrentIn(
                5L, List.of(WaitingStatus.WAITING, WaitingStatus.CALLED), WaitingStatus.CANCELED))
                .willReturn(1);

        waitingService.cancel(1L, 5L);

        then(waitingRepository).should().updateStatusIfCurrentIn(
                5L, List.of(WaitingStatus.WAITING, WaitingStatus.CALLED), WaitingStatus.CANCELED);
    }

    @Test
    @DisplayName("이미 종료된 웨이팅 취소는 WAITING_ALREADY_CLOSED")
    void cancel_alreadyClosed() {
        given(waitingRepository.findById(5L)).willReturn(Optional.of(waiting(5L, 1L, 3, WaitingStatus.EXPIRED)));
        given(waitingRepository.updateStatusIfCurrentIn(anyLong(), anyCollection(), any())).willReturn(0);

        assertThatThrownBy(() -> waitingService.cancel(1L, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WAITING_ALREADY_CLOSED);
    }

    @Test
    @DisplayName("남의 웨이팅 취소는 NOT_WAITING_OWNER")
    void cancel_notOwner() {
        given(waitingRepository.findById(5L)).willReturn(Optional.of(waiting(5L, 1L, 3, WaitingStatus.WAITING)));

        assertThatThrownBy(() -> waitingService.cancel(2L, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_WAITING_OWNER);
        then(waitingRepository).should(never()).updateStatusIfCurrentIn(anyLong(), anyCollection(), any());
    }
}
