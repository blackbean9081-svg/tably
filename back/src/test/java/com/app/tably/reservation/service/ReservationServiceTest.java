package com.app.tably.reservation.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.payment.service.PaymentService;
import com.app.tably.payment.service.RefundCalculator;
import com.app.tably.reservation.dto.AppealRequestDto;
import com.app.tably.reservation.dto.ReservationHoldRequestDto;
import com.app.tably.reservation.entity.AppealStatus;
import com.app.tably.reservation.entity.NoShowAppeal;
import com.app.tably.reservation.entity.Reservation;
import com.app.tably.reservation.entity.ReservationStatus;
import com.app.tably.reservation.repository.NoShowAppealRepository;
import com.app.tably.reservation.repository.ReservationRepository;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.slot.entity.Slot;
import com.app.tably.slot.entity.SlotStatus;
import com.app.tably.slot.repository.SlotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private NoShowAppealRepository noShowAppealRepository;

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    // 게이트는 1막 기본값(무조건 통과)으로 두고, 게이트 자체 동작은 RedisSlotHoldGateTest에서 검증
    @Spy
    private SlotHoldGate slotHoldGate = new NoopSlotHoldGate();

    @InjectMocks
    private ReservationService reservationService;

    private Member guest(Long id) {
        return Member.builder().id(id).email("guest@tably.com").password("pw").name("김지현").role(Role.GUEST).build();
    }

    private Slot openSlot() {
        return slotOn(LocalDate.of(2026, 8, 15));
    }

    private Slot slotOn(LocalDate slotDate) {
        Member owner = Member.builder().id(99L).email("owner@tably.com").password("pw").name("박성호").role(Role.OWNER).build();
        Restaurant restaurant = Restaurant.builder().id(10L).owner(owner).name("스시 준").build();
        return Slot.builder().id(20L).restaurant(restaurant)
                .slotDate(slotDate).slotTime(LocalTime.of(20, 30))
                .tableNo(1).status(SlotStatus.OPEN).build();
    }

    private Reservation reservation(Long id, Long memberId, ReservationStatus status) {
        return Reservation.builder().id(id).slot(openSlot()).member(guest(memberId))
                .partySize(2).status(status).heldAt(LocalDateTime.now()).build();
    }

    private Reservation reservationOn(LocalDate slotDate, ReservationStatus status) {
        return Reservation.builder().id(100L).slot(slotOn(slotDate)).member(guest(1L))
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
    @DisplayName("남의 예약 환불액 사전 고지 조회는 NOT_RESERVATION_OWNER 예외 — 계산 위임 전에 차단")
    void getRefundPreview_notOwner() {
        given(reservationRepository.findById(100L))
                .willReturn(Optional.of(reservation(100L, 1L, ReservationStatus.CONFIRMED)));

        assertThatThrownBy(() -> reservationService.getRefundPreview(2L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_RESERVATION_OWNER);
        then(paymentService).shouldHaveNoInteractions();
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

    @Test
    @DisplayName("게이트가 거절하면 DB에 닿지 않고 즉시 SLOT_ALREADY_TAKEN (2막 Redis 게이트 경로)")
    void hold_gateRejected() {
        willReturn(false).given(slotHoldGate).tryAcquire(20L, 1L);

        assertThatThrownBy(() -> reservationService.hold(1L, new ReservationHoldRequestDto(20L, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SLOT_ALREADY_TAKEN);
        then(slotRepository).shouldHaveNoInteractions();
        then(reservationRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("게이트 통과 후 선점이 실패하면 게이트를 즉시 되돌린다 — 슬롯이 TTL까지 헛묶이지 않게")
    void hold_releasesGateOnFailure() {
        given(slotRepository.findWithLockById(20L)).willReturn(Optional.of(openSlot()));
        given(reservationRepository.existsBySlotIdAndStatusIn(20L,
                List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED))).willReturn(true);

        assertThatThrownBy(() -> reservationService.hold(1L, new ReservationHoldRequestDto(20L, 2)))
                .isInstanceOf(BusinessException.class);
        then(slotHoldGate).should().release(20L);
    }

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

    // ── 핵심영역 4 — 상태 전이 검증 ──────────────────────────────────────

    @Test
    @DisplayName("PENDING_PAYMENT→VISITED 같은 비정상 전이는 INVALID_STATUS_TRANSITION")
    void validateTransition_invalid() {
        assertThatThrownBy(() -> reservationService.validateTransition(
                ReservationStatus.PENDING_PAYMENT, ReservationStatus.VISITED))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("상태 머신의 허용 전이는 통과한다 (requirements.md 4장)")
    void validateTransition_allowed() {
        assertThatCode(() -> {
            reservationService.validateTransition(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED);
            reservationService.validateTransition(ReservationStatus.PENDING_PAYMENT, ReservationStatus.EXPIRED);
            reservationService.validateTransition(ReservationStatus.CONFIRMED, ReservationStatus.CANCELED_BY_USER);
            reservationService.validateTransition(ReservationStatus.CONFIRMED, ReservationStatus.CANCELED_BY_SHOP);
            reservationService.validateTransition(ReservationStatus.CONFIRMED, ReservationStatus.VISITED);
            reservationService.validateTransition(ReservationStatus.CONFIRMED, ReservationStatus.NO_SHOW);
            reservationService.validateTransition(ReservationStatus.NO_SHOW, ReservationStatus.VISITED);
            reservationService.validateTransition(ReservationStatus.NO_SHOW, ReservationStatus.NO_SHOW_REVOKED);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("종결 상태(EXPIRED, VISITED 등)에서는 어떤 전이도 불가")
    void validateTransition_terminal() {
        for (ReservationStatus terminal : List.of(ReservationStatus.EXPIRED, ReservationStatus.VISITED,
                ReservationStatus.CANCELED_BY_USER, ReservationStatus.CANCELED_BY_SHOP,
                ReservationStatus.NO_SHOW_REVOKED)) {
            for (ReservationStatus target : ReservationStatus.values()) {
                assertThatThrownBy(() -> reservationService.validateTransition(terminal, target))
                        .as("%s → %s", terminal, target)
                        .isInstanceOf(BusinessException.class);
            }
        }
    }

    // ── 취소 (S4) ────────────────────────────────────────────────────────

    @Test
    @DisplayName("CONFIRMED 예약 취소 — CANCELED_BY_USER 전이 후 시점별 환불이 연결된다")
    void cancelByUser_success() {
        Reservation confirmed = reservation(100L, 1L, ReservationStatus.CONFIRMED);
        given(reservationRepository.findWithLockById(100L)).willReturn(Optional.of(confirmed));

        reservationService.cancelByUser(1L, 100L);

        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CANCELED_BY_USER);
        then(paymentService).should().refundOnCancel(confirmed, RefundCalculator.CancelCause.USER);
    }

    @Test
    @DisplayName("결제 전(PENDING_PAYMENT) 예약의 손님 취소는 INVALID_STATUS_TRANSITION — 환불도 타지 않는다")
    void cancelByUser_invalidStatus() {
        given(reservationRepository.findWithLockById(100L))
                .willReturn(Optional.of(reservation(100L, 1L, ReservationStatus.PENDING_PAYMENT)));

        assertThatThrownBy(() -> reservationService.cancelByUser(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
        then(paymentService).should(never()).refundOnCancel(any(), any());
    }

    // ── 만료·노쇼 배치 진입점 ────────────────────────────────────────────

    @Test
    @DisplayName("선점 만료 — 조건부 일괄 UPDATE의 처리 건수를 반환한다")
    void expireOverdueHolds() {
        given(reservationRepository.updateStatusAllHeldBefore(
                eq(ReservationStatus.PENDING_PAYMENT), any(LocalDateTime.class), eq(ReservationStatus.EXPIRED)))
                .willReturn(3);

        assertThat(reservationService.expireOverdueHolds()).isEqualTo(3);
    }

    @Test
    @DisplayName("노쇼 전환 — 조건부 UPDATE가 0건이면(방문 처리와 경합 패배) false")
    void markNoShow_losesRace() {
        given(reservationRepository.updateStatusIfCurrent(
                100L, ReservationStatus.CONFIRMED, ReservationStatus.NO_SHOW)).willReturn(0);

        assertThat(reservationService.markNoShow(100L)).isFalse();
    }

    @Test
    @DisplayName("노쇼 전환 — CONFIRMED 상태면 1건 갱신되고 true, 노쇼 알림 이벤트 발행")
    void markNoShow_success() {
        given(reservationRepository.updateStatusIfCurrent(
                100L, ReservationStatus.CONFIRMED, ReservationStatus.NO_SHOW)).willReturn(1);
        given(reservationRepository.findById(100L))
                .willReturn(Optional.of(reservation(100L, 1L, ReservationStatus.NO_SHOW)));

        assertThat(reservationService.markNoShow(100L)).isTrue();
        then(eventPublisher).should().publishEvent(any(Object.class));
    }

    // ── 방문 처리·정정 (FR-11, S5) ───────────────────────────────────────

    @Test
    @DisplayName("방문 완료 — CONFIRMED→VISITED 전이 + 예약금 환불")
    void markVisited_confirmed() {
        Reservation confirmed = reservation(100L, 1L, ReservationStatus.CONFIRMED);
        given(reservationRepository.findWithLockById(100L)).willReturn(Optional.of(confirmed));

        reservationService.markVisited(99L, 100L);

        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.VISITED);
        then(paymentService).should().refundDeposit(confirmed);
    }

    @Test
    @DisplayName("남의 식당 예약 방문 처리는 NOT_RESTAURANT_OWNER")
    void markVisited_notOwner() {
        given(reservationRepository.findWithLockById(100L))
                .willReturn(Optional.of(reservation(100L, 1L, ReservationStatus.CONFIRMED)));

        assertThatThrownBy(() -> reservationService.markVisited(77L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_RESTAURANT_OWNER);
    }

    @Test
    @DisplayName("노쇼 정정 — 방문 당일이면 NO_SHOW→VISITED 허용 + 예약금 환불")
    void markVisited_noShowCorrection_sameDay() {
        Reservation noShow = reservationOn(LocalDate.now(), ReservationStatus.NO_SHOW);
        given(reservationRepository.findWithLockById(100L)).willReturn(Optional.of(noShow));

        reservationService.markVisited(99L, 100L);

        assertThat(noShow.getStatus()).isEqualTo(ReservationStatus.VISITED);
        then(paymentService).should().refundDeposit(noShow);
    }

    @Test
    @DisplayName("노쇼 정정 — 당일 자정이 지나면 NO_SHOW_CORRECTION_EXPIRED (이의신청 경로로만 번복)")
    void markVisited_noShowCorrection_expired() {
        Reservation noShow = reservationOn(LocalDate.now().minusDays(1), ReservationStatus.NO_SHOW);
        given(reservationRepository.findWithLockById(100L)).willReturn(Optional.of(noShow));

        assertThatThrownBy(() -> reservationService.markVisited(99L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NO_SHOW_CORRECTION_EXPIRED);
        assertThat(noShow.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        then(paymentService).should(never()).refundDeposit(any());
    }

    // ── 식당 귀책 취소 (FR-10, S8) ───────────────────────────────────────

    @Test
    @DisplayName("식당 취소 — CANCELED_BY_SHOP 전이 후 전액 환불(SHOP 귀책)이 연결된다")
    void cancelByShop_success() {
        Reservation confirmed = reservation(100L, 1L, ReservationStatus.CONFIRMED);
        given(reservationRepository.findWithLockById(100L)).willReturn(Optional.of(confirmed));

        reservationService.cancelByShop(100L);

        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CANCELED_BY_SHOP);
        then(paymentService).should().refundOnCancel(confirmed, RefundCalculator.CancelCause.SHOP);
    }

    // ── 노쇼 이의신청 (FR-13, S5) ────────────────────────────────────────

    @Test
    @DisplayName("이의신청 접수 — NO_SHOW 예약의 본인만, OPEN 상태로 저장된다")
    void fileNoShowAppeal_success() {
        Reservation noShow = reservation(100L, 1L, ReservationStatus.NO_SHOW);
        given(reservationRepository.findById(100L)).willReturn(Optional.of(noShow));
        given(noShowAppealRepository.existsByReservationIdAndStatus(100L, AppealStatus.OPEN)).willReturn(false);
        given(noShowAppealRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        var result = reservationService.fileNoShowAppeal(1L, 100L,
                new AppealRequestDto("멀쩡히 방문해서 식사했습니다"));

        assertThat(result.status()).isEqualTo(AppealStatus.OPEN);
        assertThat(result.reason()).isEqualTo("멀쩡히 방문해서 식사했습니다");
    }

    @Test
    @DisplayName("NO_SHOW가 아닌 예약의 이의신청은 APPEAL_NOT_ALLOWED")
    void fileNoShowAppeal_notNoShow() {
        given(reservationRepository.findById(100L))
                .willReturn(Optional.of(reservation(100L, 1L, ReservationStatus.CONFIRMED)));

        assertThatThrownBy(() -> reservationService.fileNoShowAppeal(1L, 100L, new AppealRequestDto("사유")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPEAL_NOT_ALLOWED);
    }

    @Test
    @DisplayName("이미 접수된 이의신청이 있으면 ALREADY_APPEALED")
    void fileNoShowAppeal_duplicate() {
        given(reservationRepository.findById(100L))
                .willReturn(Optional.of(reservation(100L, 1L, ReservationStatus.NO_SHOW)));
        given(noShowAppealRepository.existsByReservationIdAndStatus(100L, AppealStatus.OPEN)).willReturn(true);

        assertThatThrownBy(() -> reservationService.fileNoShowAppeal(1L, 100L, new AppealRequestDto("사유")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_APPEALED);
    }

    @Test
    @DisplayName("이의신청 인용 — NO_SHOW_REVOKED 전이 + 예약금 환불 + ACCEPTED 기록")
    void acceptAppeal_success() {
        Reservation noShow = reservation(100L, 1L, ReservationStatus.NO_SHOW);
        NoShowAppeal appeal = NoShowAppeal.builder().id(7L).reservation(noShow).member(guest(1L))
                .reason("사유").status(AppealStatus.OPEN).createdAt(LocalDateTime.now()).build();
        given(noShowAppealRepository.findById(7L)).willReturn(Optional.of(appeal));
        given(reservationRepository.findWithLockById(100L)).willReturn(Optional.of(noShow));

        var result = reservationService.acceptAppeal(7L);

        assertThat(noShow.getStatus()).isEqualTo(ReservationStatus.NO_SHOW_REVOKED);
        assertThat(result.status()).isEqualTo(AppealStatus.ACCEPTED);
        assertThat(result.decidedAt()).isNotNull();
        then(paymentService).should().refundDeposit(noShow);
    }

    @Test
    @DisplayName("이미 처리된 이의신청 재심사는 APPEAL_ALREADY_DECIDED")
    void acceptAppeal_alreadyDecided() {
        NoShowAppeal decided = NoShowAppeal.builder().id(7L)
                .reservation(reservation(100L, 1L, ReservationStatus.NO_SHOW_REVOKED)).member(guest(1L))
                .reason("사유").status(AppealStatus.ACCEPTED)
                .createdAt(LocalDateTime.now()).decidedAt(LocalDateTime.now()).build();
        given(noShowAppealRepository.findById(7L)).willReturn(Optional.of(decided));

        assertThatThrownBy(() -> reservationService.acceptAppeal(7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPEAL_ALREADY_DECIDED);
    }

    @Test
    @DisplayName("이의신청 기각 — 예약 상태는 그대로, REJECTED 기록")
    void rejectAppeal_success() {
        Reservation noShow = reservation(100L, 1L, ReservationStatus.NO_SHOW);
        NoShowAppeal appeal = NoShowAppeal.builder().id(7L).reservation(noShow).member(guest(1L))
                .reason("사유").status(AppealStatus.OPEN).createdAt(LocalDateTime.now()).build();
        given(noShowAppealRepository.findById(7L)).willReturn(Optional.of(appeal));

        var result = reservationService.rejectAppeal(7L);

        assertThat(result.status()).isEqualTo(AppealStatus.REJECTED);
        assertThat(noShow.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        then(paymentService).should(never()).refundDeposit(any());
    }
}
