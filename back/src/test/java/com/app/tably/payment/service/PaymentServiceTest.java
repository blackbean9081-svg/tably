package com.app.tably.payment.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.payment.dto.PaymentApproveRequestDto;
import com.app.tably.payment.entity.Payment;
import com.app.tably.payment.entity.PaymentStatus;
import com.app.tably.payment.entity.PaymentType;
import com.app.tably.payment.pg.PgClient;
import com.app.tably.payment.pg.PgDeclinedException;
import com.app.tably.payment.pg.PgTimeoutException;
import com.app.tably.payment.repository.PaymentRepository;
import com.app.tably.reservation.entity.Reservation;
import com.app.tably.reservation.entity.ReservationStatus;
import com.app.tably.reservation.repository.ReservationRepository;
import com.app.tably.restaurant.entity.ReservationPolicy;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.ReservationPolicyRepository;
import com.app.tably.slot.entity.Slot;
import com.app.tably.slot.entity.SlotStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationPolicyRepository reservationPolicyRepository;

    @Mock
    private RefundCalculator refundCalculator;

    @Mock
    private PgClient pgClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    private Restaurant restaurant() {
        Member owner = Member.builder().id(99L).email("owner@tably.com").password("pw").name("박성호").role(Role.OWNER).build();
        return Restaurant.builder().id(10L).owner(owner).name("스시 준").build();
    }

    private Reservation reservation(Long memberId, ReservationStatus status, LocalDateTime heldAt) {
        Member guest = Member.builder().id(memberId).email("guest@tably.com").password("pw").name("김지현").role(Role.GUEST).build();
        Slot slot = Slot.builder().id(20L).restaurant(restaurant())
                .slotDate(LocalDate.of(2026, 8, 15)).slotTime(LocalTime.of(20, 30))
                .tableNo(1).status(SlotStatus.OPEN).build();
        return Reservation.builder().id(100L).slot(slot).member(guest)
                .partySize(2).status(status).heldAt(heldAt).build();
    }

    private Reservation pendingReservation(Long memberId) {
        return reservation(memberId, ReservationStatus.PENDING_PAYMENT, LocalDateTime.now());
    }

    private ReservationPolicy policy() {
        return ReservationPolicy.builder().restaurant(restaurant())
                .depositPerPerson(20000).refundRule("7:100,3:50,1:0")
                .openRule("MONTHLY:1:10:00").tablesPerTime(4).slotTimes("18:00,20:30").build();
    }

    private Payment approvedPay(Reservation reservation, String key) {
        return Payment.builder().id(500L).reservation(reservation).type(PaymentType.PAY)
                .amount(40000).status(PaymentStatus.APPROVED)
                .idempotencyKey(key).pgTxId("mock-pay-1").createdAt(LocalDateTime.now()).build();
    }

    private void stubPolicyLookup() {
        given(reservationPolicyRepository.findByRestaurantId(10L)).willReturn(Optional.of(policy()));
    }

    private void stubSavePassThrough() {
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    // ── approve — 멱등성 (핵심영역 2) ─────────────────────────────────────

    @Test
    @DisplayName("같은 idempotency_key 재요청은 새 청구 없이 기존 결과를 반환한다")
    void approve_idempotentReplay() {
        Reservation reservation = pendingReservation(1L);
        given(paymentRepository.findByIdempotencyKey("key-1"))
                .willReturn(Optional.of(approvedPay(reservation, "key-1")));

        var result = paymentService.approve(1L, new PaymentApproveRequestDto(100L, 40000, "key-1"));

        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
        then(paymentRepository).should(never()).save(any());
        then(pgClient).should(never()).approve(anyString(), anyInt());
        then(reservationRepository).should(never()).findWithLockById(any());
    }

    @Test
    @DisplayName("락 대기 중 첫 요청이 커밋된 더블 클릭 — 락 획득 후 재확인에서 기존 결과 반환")
    void approve_idempotentAfterLockWait() {
        Reservation reservation = pendingReservation(1L);
        given(paymentRepository.findByIdempotencyKey("key-1"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(approvedPay(reservation, "key-1")));
        given(reservationRepository.findWithLockById(100L)).willReturn(Optional.of(reservation));

        var result = paymentService.approve(1L, new PaymentApproveRequestDto(100L, 40000, "key-1"));

        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
        then(paymentRepository).should(never()).save(any());
        then(pgClient).should(never()).approve(anyString(), anyInt());
    }

    // ── approve — 검증 ────────────────────────────────────────────────────

    @Test
    @DisplayName("남의 예약 결제는 NOT_RESERVATION_OWNER")
    void approve_notOwner() {
        given(paymentRepository.findByIdempotencyKey("key-1")).willReturn(Optional.empty());
        given(reservationRepository.findWithLockById(100L)).willReturn(Optional.of(pendingReservation(1L)));

        assertThatThrownBy(() -> paymentService.approve(2L, new PaymentApproveRequestDto(100L, 40000, "key-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_RESERVATION_OWNER);
    }

    @Test
    @DisplayName("PENDING_PAYMENT가 아닌 예약 결제는 INVALID_STATUS_TRANSITION")
    void approve_invalidStatus() {
        given(paymentRepository.findByIdempotencyKey("key-1")).willReturn(Optional.empty());
        given(reservationRepository.findWithLockById(100L))
                .willReturn(Optional.of(reservation(1L, ReservationStatus.CONFIRMED, LocalDateTime.now())));

        assertThatThrownBy(() -> paymentService.approve(1L, new PaymentApproveRequestDto(100L, 40000, "key-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("held_at + 10분이 지난 예약 결제는 PAYMENT_TIME_EXPIRED")
    void approve_holdExpired() {
        given(paymentRepository.findByIdempotencyKey("key-1")).willReturn(Optional.empty());
        given(reservationRepository.findWithLockById(100L))
                .willReturn(Optional.of(reservation(1L, ReservationStatus.PENDING_PAYMENT,
                        LocalDateTime.now().minusMinutes(11))));

        assertThatThrownBy(() -> paymentService.approve(1L, new PaymentApproveRequestDto(100L, 40000, "key-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TIME_EXPIRED);
    }

    @Test
    @DisplayName("정책 예약금 × 인원수와 다른 금액은 PAYMENT_AMOUNT_MISMATCH")
    void approve_amountMismatch() {
        given(paymentRepository.findByIdempotencyKey("key-1")).willReturn(Optional.empty());
        given(reservationRepository.findWithLockById(100L)).willReturn(Optional.of(pendingReservation(1L)));
        stubPolicyLookup();

        assertThatThrownBy(() -> paymentService.approve(1L, new PaymentApproveRequestDto(100L, 20000, "key-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    // ── approve — PG 응답 시나리오 ────────────────────────────────────────

    @Test
    @DisplayName("PG 승인 → payment APPROVED + pg_tx_id 저장 + 예약 CONFIRMED 전이")
    void approve_success() {
        Reservation reservation = pendingReservation(1L);
        given(paymentRepository.findByIdempotencyKey("key-1")).willReturn(Optional.empty());
        given(reservationRepository.findWithLockById(100L)).willReturn(Optional.of(reservation));
        stubPolicyLookup();
        stubSavePassThrough();
        given(pgClient.approve("key-1", 40000)).willReturn("pg-tx-777");

        var result = paymentService.approve(1L, new PaymentApproveRequestDto(100L, 40000, "key-1"));

        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(result.pgTxId()).isEqualTo("pg-tx-777");
        assertThat(result.type()).isEqualTo(PaymentType.PAY);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("PG 거절 → payment FAILED, 예약은 PENDING_PAYMENT 유지 (재시도 가능)")
    void approve_declined() {
        Reservation reservation = pendingReservation(1L);
        given(paymentRepository.findByIdempotencyKey("key-1")).willReturn(Optional.empty());
        given(reservationRepository.findWithLockById(100L)).willReturn(Optional.of(reservation));
        stubPolicyLookup();
        stubSavePassThrough();
        given(pgClient.approve("key-1", 40000)).willThrow(new PgDeclinedException("한도 초과"));

        var result = paymentService.approve(1L, new PaymentApproveRequestDto(100L, 40000, "key-1"));

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("PG 타임아웃 → FAILED가 아니라 UNKNOWN으로 기록 (재확인 대상, S3)")
    void approve_timeout() {
        Reservation reservation = pendingReservation(1L);
        given(paymentRepository.findByIdempotencyKey("key-1")).willReturn(Optional.empty());
        given(reservationRepository.findWithLockById(100L)).willReturn(Optional.of(reservation));
        stubPolicyLookup();
        stubSavePassThrough();
        given(pgClient.approve("key-1", 40000)).willThrow(new PgTimeoutException());

        var result = paymentService.approve(1L, new PaymentApproveRequestDto(100L, 40000, "key-1"));

        assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    // ── refundOnCancel (S4, FR-08/09) ────────────────────────────────────

    @Test
    @DisplayName("전액 환불 — REFUND 행 APPROVED, 원 결제 행은 CANCELED 표시")
    void refundOnCancel_full() {
        Reservation reservation = reservation(1L, ReservationStatus.CANCELED_BY_USER, LocalDateTime.now());
        Payment paid = approvedPay(reservation, "key-1");
        given(paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                100L, PaymentType.PAY, PaymentStatus.APPROVED)).willReturn(Optional.of(paid));
        stubPolicyLookup();
        given(refundCalculator.calculate(any(), any(), any(), anyInt(), any())).willReturn(40000);
        stubSavePassThrough();
        given(pgClient.cancel("mock-pay-1", 40000)).willReturn("cancel-tx-1");

        int refunded = paymentService.refundOnCancel(reservation, RefundCalculator.CancelCause.USER);

        assertThat(refunded).isEqualTo(40000);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    @DisplayName("부분 환불(50%) — 원 결제 행은 PARTIAL_CANCELED 표시")
    void refundOnCancel_partial() {
        Reservation reservation = reservation(1L, ReservationStatus.CANCELED_BY_USER, LocalDateTime.now());
        Payment paid = approvedPay(reservation, "key-1");
        given(paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                100L, PaymentType.PAY, PaymentStatus.APPROVED)).willReturn(Optional.of(paid));
        stubPolicyLookup();
        given(refundCalculator.calculate(any(), any(), any(), anyInt(), any())).willReturn(20000);
        stubSavePassThrough();
        given(pgClient.cancel("mock-pay-1", 20000)).willReturn("cancel-tx-1");

        int refunded = paymentService.refundOnCancel(reservation, RefundCalculator.CancelCause.USER);

        assertThat(refunded).isEqualTo(20000);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
    }

    @Test
    @DisplayName("환불액 0원이면 REFUND 행을 만들지 않는다")
    void refundOnCancel_zero() {
        Reservation reservation = reservation(1L, ReservationStatus.CANCELED_BY_USER, LocalDateTime.now());
        given(paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                100L, PaymentType.PAY, PaymentStatus.APPROVED))
                .willReturn(Optional.of(approvedPay(reservation, "key-1")));
        stubPolicyLookup();
        given(refundCalculator.calculate(any(), any(), any(), anyInt(), any())).willReturn(0);

        int refunded = paymentService.refundOnCancel(reservation, RefundCalculator.CancelCause.USER);

        assertThat(refunded).isZero();
        then(paymentRepository).should(never()).save(any());
        then(pgClient).should(never()).cancel(anyString(), anyInt());
    }

    @Test
    @DisplayName("환불 PG 실패 — 취소는 성립시키고 REFUND 행을 FAILED로 남긴다 (FR-09 재처리 대상)")
    void refundOnCancel_pgFailed() {
        Reservation reservation = reservation(1L, ReservationStatus.CANCELED_BY_USER, LocalDateTime.now());
        Payment paid = approvedPay(reservation, "key-1");
        given(paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                100L, PaymentType.PAY, PaymentStatus.APPROVED)).willReturn(Optional.of(paid));
        stubPolicyLookup();
        given(refundCalculator.calculate(any(), any(), any(), anyInt(), any())).willReturn(40000);
        java.util.concurrent.atomic.AtomicReference<Payment> savedRefund = new java.util.concurrent.atomic.AtomicReference<>();
        given(paymentRepository.save(any())).willAnswer(inv -> {
            savedRefund.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        given(pgClient.cancel("mock-pay-1", 40000)).willThrow(new PgDeclinedException("PG 오류"));

        int refunded = paymentService.refundOnCancel(reservation, RefundCalculator.CancelCause.USER);

        assertThat(refunded).isEqualTo(40000);
        assertThat(savedRefund.get().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    // ── refundDeposit (FR-11/13 — 방문·철회 시 예약금 전액 환불) ─────────

    @Test
    @DisplayName("예약금 환불 — 승인된 결제 전액의 REFUND 행이 만들어지고 원 결제는 CANCELED")
    void refundDeposit_full() {
        Reservation reservation = reservation(1L, ReservationStatus.VISITED, LocalDateTime.now());
        Payment paid = approvedPay(reservation, "key-1");
        given(paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                100L, PaymentType.PAY, PaymentStatus.APPROVED)).willReturn(Optional.of(paid));
        stubSavePassThrough();
        given(pgClient.cancel("mock-pay-1", 40000)).willReturn("cancel-tx-1");

        int refunded = paymentService.refundDeposit(reservation);

        assertThat(refunded).isEqualTo(40000);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    @DisplayName("승인된 결제가 없으면 환불 없이 0원")
    void refundDeposit_noApprovedPay() {
        Reservation reservation = reservation(1L, ReservationStatus.VISITED, LocalDateTime.now());
        given(paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                100L, PaymentType.PAY, PaymentStatus.APPROVED)).willReturn(Optional.empty());

        assertThat(paymentService.refundDeposit(reservation)).isZero();
        then(pgClient).should(never()).cancel(anyString(), anyInt());
    }

    // ── reconcile (FR-07 — UNKNOWN 재확인) ───────────────────────────────

    private Payment unknownPay(Reservation reservation, String key) {
        return Payment.builder().id(600L).reservation(reservation).type(PaymentType.PAY)
                .amount(40000).status(PaymentStatus.UNKNOWN)
                .idempotencyKey(key).createdAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("재확인 결과 승인 + 예약이 아직 PENDING_PAYMENT면 CONFIRMED로 복구")
    void reconcile_approvedAndReservationPending() {
        Reservation reservation = pendingReservation(1L);
        Payment unknown = unknownPay(reservation, "pg-timeout-approved-1");
        given(paymentRepository.findById(600L)).willReturn(Optional.of(unknown));
        given(pgClient.inquire("pg-timeout-approved-1")).willReturn(
                com.app.tably.payment.pg.PgInquiryResult.approved("recovered-tx-1"));
        given(reservationRepository.findWithLockById(100L)).willReturn(Optional.of(reservation));

        boolean resolved = paymentService.reconcile(600L);

        assertThat(resolved).isTrue();
        assertThat(unknown.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(unknown.getPgTxId()).isEqualTo("recovered-tx-1");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("재확인 결과 승인이지만 예약이 이미 만료 — '돈은 나갔는데 예약은 없는' 상태를 전액 환불로 복구 (S3)")
    void reconcile_approvedButReservationExpired() {
        Reservation reservation = reservation(1L, ReservationStatus.EXPIRED, LocalDateTime.now().minusMinutes(20));
        Payment unknown = unknownPay(reservation, "pg-timeout-approved-1");
        given(paymentRepository.findById(600L)).willReturn(Optional.of(unknown));
        given(pgClient.inquire("pg-timeout-approved-1")).willReturn(
                com.app.tably.payment.pg.PgInquiryResult.approved("recovered-tx-1"));
        given(reservationRepository.findWithLockById(100L)).willReturn(Optional.of(reservation));
        java.util.concurrent.atomic.AtomicReference<Payment> savedRefund = new java.util.concurrent.atomic.AtomicReference<>();
        given(paymentRepository.save(any())).willAnswer(inv -> {
            savedRefund.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        given(pgClient.cancel("recovered-tx-1", 40000)).willReturn("cancel-tx-1");

        boolean resolved = paymentService.reconcile(600L);

        assertThat(resolved).isTrue();
        assertThat(savedRefund.get().getType()).isEqualTo(PaymentType.REFUND);
        assertThat(savedRefund.get().getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(unknown.getStatus()).isEqualTo(PaymentStatus.CANCELED);   // 승인 확인 직후 전액 취소 표시
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    @DisplayName("재확인 결과 미승인 — FAILED로 확정")
    void reconcile_failed() {
        Payment unknown = unknownPay(pendingReservation(1L), "pg-timeout-1");
        given(paymentRepository.findById(600L)).willReturn(Optional.of(unknown));
        given(pgClient.inquire("pg-timeout-1")).willReturn(com.app.tably.payment.pg.PgInquiryResult.failed());

        assertThat(paymentService.reconcile(600L)).isTrue();
        assertThat(unknown.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("이미 확정된 결제의 재확인은 아무것도 하지 않는다 (배치 재실행 멱등)")
    void reconcile_alreadyResolved() {
        given(paymentRepository.findById(600L))
                .willReturn(Optional.of(approvedPay(pendingReservation(1L), "key-1")));

        assertThat(paymentService.reconcile(600L)).isFalse();
        then(pgClient).should(never()).inquire(anyString());
    }

    // ── retryRefund (FR-09 — 환불 실패 재처리) ───────────────────────────

    private Payment failedRefund(Reservation reservation) {
        return Payment.builder().id(700L).reservation(reservation).type(PaymentType.REFUND)
                .amount(40000).status(PaymentStatus.FAILED)
                .idempotencyKey("rf-500-x").createdAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("환불 재처리 성공 — REFUND 행 APPROVED, 원 결제 CANCELED")
    void retryRefund_success() {
        Reservation reservation = reservation(1L, ReservationStatus.CANCELED_BY_USER, LocalDateTime.now());
        Payment paid = approvedPay(reservation, "key-1");
        Payment refund = failedRefund(reservation);
        given(paymentRepository.findById(700L)).willReturn(Optional.of(refund));
        given(paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                100L, PaymentType.PAY, PaymentStatus.APPROVED)).willReturn(Optional.of(paid));
        given(pgClient.cancel("mock-pay-1", 40000)).willReturn("cancel-tx-2");

        assertThat(paymentService.retryRefund(700L)).isTrue();
        assertThat(refund.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    @DisplayName("환불 재처리 거절 — FAILED 유지, 다음 주기에 다시 시도")
    void retryRefund_declinedAgain() {
        Reservation reservation = reservation(1L, ReservationStatus.CANCELED_BY_USER, LocalDateTime.now());
        Payment refund = failedRefund(reservation);
        given(paymentRepository.findById(700L)).willReturn(Optional.of(refund));
        given(paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                100L, PaymentType.PAY, PaymentStatus.APPROVED))
                .willReturn(Optional.of(approvedPay(reservation, "key-1")));
        given(pgClient.cancel("mock-pay-1", 40000)).willThrow(new PgDeclinedException("PG 오류"));

        assertThat(paymentService.retryRefund(700L)).isFalse();
        assertThat(refund.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    // ── 조회 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("남의 예약 결제 이력 조회는 NOT_RESERVATION_OWNER 예외")
    void getPayments_notOwner() {
        given(reservationRepository.findById(100L)).willReturn(Optional.of(pendingReservation(1L)));

        assertThatThrownBy(() -> paymentService.getPayments(2L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_RESERVATION_OWNER);
    }
}
