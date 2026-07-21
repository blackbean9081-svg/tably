package com.app.tably.reservation.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.reservation.dto.ReservationHoldRequestDto;
import com.app.tably.reservation.entity.Reservation;
import com.app.tably.reservation.entity.ReservationStatus;
import com.app.tably.reservation.repository.ReservationRepository;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.slot.entity.Slot;
import com.app.tably.slot.entity.SlotStatus;
import com.app.tably.slot.repository.SlotRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private ReservationService reservationService;

    private Member guest(Long id) {
        return Member.builder().id(id).email("guest@tably.com").password("pw").name("김지현").role(Role.GUEST).build();
    }

    private Slot openSlot() {
        Member owner = Member.builder().id(99L).email("owner@tably.com").password("pw").name("박성호").role(Role.OWNER).build();
        Restaurant restaurant = Restaurant.builder().id(10L).owner(owner).name("스시 준").build();
        return Slot.builder().id(20L).restaurant(restaurant)
                .slotDate(LocalDate.of(2026, 8, 15)).slotTime(LocalTime.of(20, 30))
                .tableNo(1).status(SlotStatus.OPEN).build();
    }

    private Reservation reservation(Long id, Long memberId, ReservationStatus status) {
        return Reservation.builder().id(id).slot(openSlot()).member(guest(memberId))
                .partySize(2).status(status).heldAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("내 예약 목록 조회")
    void getMyReservations() {
        given(reservationRepository.findAllByMemberIdOrderByIdDesc(1L))
                .willReturn(List.of(reservation(100L, 1L, ReservationStatus.CONFIRMED)));

        var result = reservationService.getMyReservations(1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().restaurantName()).isEqualTo("스시 준");
    }

    @Test
    @DisplayName("남의 예약 단건 조회는 NOT_RESERVATION_OWNER 예외")
    void getReservation_notOwner() {
        given(reservationRepository.findById(100L))
                .willReturn(Optional.of(reservation(100L, 1L, ReservationStatus.CONFIRMED)));

        assertThatThrownBy(() -> reservationService.getReservation(2L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_RESERVATION_OWNER);
    }

    @Test
    @DisplayName("없는 슬롯 선점은 SLOT_NOT_FOUND")
    void hold_slotNotFound() {
        given(slotRepository.findWithLockById(20L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.hold(1L, new ReservationHoldRequestDto(20L, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SLOT_NOT_FOUND);
    }

    @Test
    @DisplayName("선점 성공 시 PENDING_PAYMENT 예약이 저장되고 id가 반환된다")
    void hold_success() {
        given(slotRepository.findWithLockById(20L)).willReturn(Optional.of(openSlot()));
        given(reservationRepository.existsBySlotIdAndStatusIn(any(), any())).willReturn(false);
        given(memberRepository.findById(1L)).willReturn(Optional.of(guest(1L)));
        given(reservationRepository.save(any())).willReturn(reservation(100L, 1L, ReservationStatus.PENDING_PAYMENT));

        Long id = reservationService.hold(1L, new ReservationHoldRequestDto(20L, 2));

        assertThat(id).isEqualTo(100L);
        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        then(reservationRepository).should().save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(captor.getValue().getHeldAt()).isNotNull();
    }

    // ── 핵심영역 구현 후 활성화할 명세 테스트 ──────────────────────────────

    @Test
    @DisplayName("활성 예약이 있는 슬롯 선점은 SLOT_ALREADY_TAKEN")
    void hold_alreadyTaken() {
        given(slotRepository.findWithLockById(20L)).willReturn(Optional.of(openSlot()));
        given(reservationRepository.existsBySlotIdAndStatusIn(20L,
                List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED))).willReturn(true);

        assertThatThrownBy(() -> reservationService.hold(1L, new ReservationHoldRequestDto(20L, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SLOT_ALREADY_TAKEN);
    }

    @Test
    @Disabled("핵심영역 4(상태 전이 검증) 구현 후 활성화")
    @DisplayName("[명세] PENDING_PAYMENT→VISITED 같은 비정상 전이는 INVALID_STATUS_TRANSITION")
    void validateTransition_spec() {
        assertThatThrownBy(() -> reservationService.validateTransition(
                ReservationStatus.PENDING_PAYMENT, ReservationStatus.VISITED))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("만료 대상이 없으면 0을 반환한다")
    void expireOverdueHolds_none() {
        given(reservationRepository.findAllByStatusAndHeldAtBefore(any(), any())).willReturn(List.of());

        assertThat(reservationService.expireOverdueHolds()).isZero();
    }
}
