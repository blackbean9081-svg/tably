package com.app.tably.payment.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.payment.dto.PaymentApproveRequestDto;
import com.app.tably.payment.repository.PaymentRepository;
import com.app.tably.reservation.entity.Reservation;
import com.app.tably.reservation.entity.ReservationStatus;
import com.app.tably.reservation.repository.ReservationRepository;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.slot.entity.Slot;
import com.app.tably.slot.entity.SlotStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private PaymentService paymentService;

    private Reservation reservation(Long memberId) {
        Member owner = Member.builder().id(99L).email("owner@tably.com").password("pw").name("박성호").role(Role.OWNER).build();
        Member guest = Member.builder().id(memberId).email("guest@tably.com").password("pw").name("김지현").role(Role.GUEST).build();
        Restaurant restaurant = Restaurant.builder().id(10L).owner(owner).name("스시 준").build();
        Slot slot = Slot.builder().id(20L).restaurant(restaurant)
                .slotDate(LocalDate.of(2026, 8, 15)).slotTime(LocalTime.of(20, 30))
                .tableNo(1).status(SlotStatus.OPEN).build();
        return Reservation.builder().id(100L).slot(slot).member(guest)
                .partySize(2).status(ReservationStatus.PENDING_PAYMENT).heldAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("결제 승인(approve)은 핵심영역 2 구현 전까지 UnsupportedOperationException")
    void approve_notImplementedYet() {
        assertThatThrownBy(() -> paymentService.approve(1L,
                new PaymentApproveRequestDto(100L, 40000, "key-1")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("남의 예약 결제 이력 조회는 NOT_RESERVATION_OWNER 예외")
    void getPayments_notOwner() {
        given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation(1L)));

        assertThatThrownBy(() -> paymentService.getPayments(2L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_RESERVATION_OWNER);
    }

    // ── 핵심영역 구현 후 활성화할 명세 테스트 ──────────────────────────────

    @Test
    @Disabled("핵심영역 2(결제 멱등성) 구현 후 활성화")
    @DisplayName("[명세] 같은 idempotency_key 재요청은 새 청구 없이 기존 결과를 반환한다")
    void approve_idempotent_spec() {
        // given: key-1로 APPROVED 결제가 이미 존재
        // when: 같은 key-1로 재요청
        // then: 새 payment 행 생성 없이 기존 APPROVED 결과 반환 (save 호출 없음)
    }

    @Test
    @Disabled("핵심영역 2(결제 멱등성) 구현 후 활성화")
    @DisplayName("[명세] PG 타임아웃 시 FAILED가 아니라 UNKNOWN으로 기록된다")
    void approve_unknown_spec() {
        // given: PG 호출이 타임아웃
        // then: payment.status == UNKNOWN (재확인 배치가 정합성을 맞출 수 있도록)
    }
}
