package com.app.tably.restaurant.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.reservation.entity.ReservationStatus;
import com.app.tably.reservation.repository.ReservationRepository;
import com.app.tably.reservation.service.ReservationService;
import com.app.tably.restaurant.dto.ClosureRequestDto;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.RestaurantRepository;
import com.app.tably.slot.entity.SlotStatus;
import com.app.tably.slot.repository.SlotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyLong;

@ExtendWith(MockitoExtension.class)
class RestaurantClosureServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 20);
    private static final LocalDate END = LocalDate.of(2026, 8, 29);

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private RestaurantClosureService closureService;

    private Member owner() {
        return Member.builder().id(99L).email("owner@tably.com").password("pw").name("박성호").role(Role.OWNER).build();
    }

    private Restaurant restaurant() {
        return Restaurant.builder().id(10L).owner(owner()).name("스시 준").build();
    }

    @Test
    @DisplayName("휴업 처리 — 슬롯 닫기 + 확정 예약 건별 취소, 실패 건은 성공 건을 막지 않는다 (S8)")
    void closeTemporarily_partialFailure() {
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant()));
        given(memberRepository.findById(99L)).willReturn(Optional.of(owner()));
        given(slotRepository.updateStatusAllBetween(10L, START, END, SlotStatus.OPEN, SlotStatus.CLOSED))
                .willReturn(20);
        given(reservationRepository.findIdsByRestaurantAndSlotDateBetweenAndStatus(
                10L, START, END, ReservationStatus.CONFIRMED)).willReturn(List.of(1L, 2L, 3L));
        willAnswer(inv -> {
            if (inv.getArgument(0, Long.class).equals(2L)) {
                throw new RuntimeException("PG 오류");
            }
            return null;
        }).given(reservationService).cancelByShop(anyLong());

        var result = closureService.closeTemporarily(99L, 10L, new ClosureRequestDto(START, END));

        assertThat(result.slotsClosed()).isEqualTo(20);
        assertThat(result.totalReservations()).isEqualTo(3);
        assertThat(result.canceled()).isEqualTo(2);
        assertThat(result.failedReservationIds()).containsExactly(2L);
        then(reservationService).should().cancelByShop(1L);
        then(reservationService).should().cancelByShop(3L);
    }

    @Test
    @DisplayName("사장이 아닌 요청자(ADMIN 제외)는 NOT_RESTAURANT_OWNER")
    void closeTemporarily_notOwner() {
        Member stranger = Member.builder().id(77L).email("x@tably.com").password("pw").name("타인").role(Role.OWNER).build();
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant()));
        given(memberRepository.findById(77L)).willReturn(Optional.of(stranger));

        assertThatThrownBy(() -> closureService.closeTemporarily(77L, 10L, new ClosureRequestDto(START, END)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_RESTAURANT_OWNER);
        then(reservationService).should(never()).cancelByShop(anyLong());
    }

    @Test
    @DisplayName("운영자(ADMIN)는 소유권 없이도 휴업 처리 가능")
    void closeTemporarily_admin() {
        Member admin = Member.builder().id(50L).email("admin@tably.com").password("pw").name("운영자").role(Role.ADMIN).build();
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant()));
        given(memberRepository.findById(50L)).willReturn(Optional.of(admin));
        given(slotRepository.updateStatusAllBetween(10L, START, END, SlotStatus.OPEN, SlotStatus.CLOSED))
                .willReturn(0);
        given(reservationRepository.findIdsByRestaurantAndSlotDateBetweenAndStatus(
                10L, START, END, ReservationStatus.CONFIRMED)).willReturn(List.of());

        var result = closureService.closeTemporarily(50L, 10L, new ClosureRequestDto(START, END));

        assertThat(result.totalReservations()).isZero();
    }

    @Test
    @DisplayName("종료일이 시작일보다 앞서면 INVALID_CLOSURE_PERIOD")
    void closeTemporarily_invalidPeriod() {
        assertThatThrownBy(() -> closureService.closeTemporarily(99L, 10L,
                new ClosureRequestDto(END, START)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CLOSURE_PERIOD);
    }
}
