package com.app.tably.payment.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.notification.entity.NotificationType;
import com.app.tably.notification.event.NotificationEvent;
import com.app.tably.payment.dto.PaymentApproveRequestDto;
import com.app.tably.payment.dto.PaymentResponseDto;
import com.app.tably.payment.entity.Payment;
import com.app.tably.payment.entity.PaymentStatus;
import com.app.tably.payment.entity.PaymentType;
import com.app.tably.payment.pg.PgClient;
import com.app.tably.payment.pg.PgDeclinedException;
import com.app.tably.payment.pg.PgInquiryResult;
import com.app.tably.payment.pg.PgTimeoutException;
import com.app.tably.payment.repository.PaymentRepository;
import com.app.tably.reservation.dto.RefundPreviewResponseDto;
import com.app.tably.reservation.entity.Reservation;
import com.app.tably.reservation.entity.ReservationStatus;
import com.app.tably.reservation.repository.ReservationRepository;
import com.app.tably.reservation.service.ReservationService;
import com.app.tably.restaurant.entity.ReservationPolicy;
import com.app.tably.restaurant.repository.ReservationPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationPolicyRepository reservationPolicyRepository;
    private final RefundCalculator refundCalculator;
    private final PgClient pgClient;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * FR-06/07: 예약금 결제 승인 (핵심영역 2).
     *
     * 멱등성: 같은 idempotency_key 재요청은 새 청구 없이 기존 결과를 반환한다.
     *  - 락 전 조회: 이미 커밋된 재요청(뒤늦은 재전송)의 빠른 경로
     *  - 락 후 재조회: 더블 클릭처럼 첫 요청이 진행 중인 경합 —
     *    두 번째 요청은 예약 행 락에서 대기하다가, 락을 얻은 뒤 첫 요청의 커밋 결과를 본다
     *
     * 경합: 예약 행을 PESSIMISTIC_WRITE로 잠가 만료 배치·중복 결제와 직렬화한다.
     * 만료 배치는 조건부 UPDATE라 이 행의 락이 풀릴 때까지 대기 후 조건 불일치로 비켜 간다.
     * (행 락을 쥔 채 PG를 호출하는 트레이드오프는 mock PG 기준 — 실 PG 연동 시 재검토)
     */
    @Transactional
    public PaymentResponseDto approve(Long memberId, PaymentApproveRequestDto request) {
        Optional<Payment> replayed = paymentRepository.findByIdempotencyKey(request.idempotencyKey());
        if (replayed.isPresent()) {
            return PaymentResponseDto.from(replayed.get());
        }

        Reservation reservation = reservationRepository.findWithLockById(request.reservationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_RESERVATION_OWNER);
        }

        Optional<Payment> committedWhileWaiting = paymentRepository.findByIdempotencyKey(request.idempotencyKey());
        if (committedWhileWaiting.isPresent()) {
            return PaymentResponseDto.from(committedWhileWaiting.get());
        }

        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (reservation.getHeldAt().plus(ReservationService.HOLD_TIMEOUT).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PAYMENT_TIME_EXPIRED);
        }

        ReservationPolicy policy = policyOf(reservation);
        int expected = policy.getDepositPerPerson() * reservation.getPartySize();
        if (request.amount() != expected) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // 결제 시도 1번 = payment 1행 — READY로 생성 후 결과에 따라 상태만 갱신 (행 재사용 금지)
        Payment payment = paymentRepository.save(Payment.builder()
                .reservation(reservation)
                .type(PaymentType.PAY)
                .amount(request.amount())
                .status(PaymentStatus.READY)
                .idempotencyKey(request.idempotencyKey())
                .createdAt(LocalDateTime.now())
                .build());

        try {
            String pgTxId = pgClient.approve(request.idempotencyKey(), request.amount());
            payment.approve(pgTxId);
            transition(reservation, ReservationStatus.CONFIRMED);
            publishConfirmed(reservation);
        } catch (PgDeclinedException e) {
            // 거절은 실패 확정 — 예약은 PENDING_PAYMENT 유지, 시한 내 새 키로 재시도 가능
            payment.fail();
        } catch (PgTimeoutException e) {
            // 응답 유실은 실패로 단정하지 않는다 — UNKNOWN으로 남겨 재확인(FR-07) 대상에 올린다
            payment.markUnknown();
            log.warn("PG 응답 유실 — payment {} UNKNOWN 기록, 재확인 대상", payment.getId());
        }
        return PaymentResponseDto.from(payment);
    }

    /**
     * S4/S8, FR-08/09: 취소 흐름의 환불 기록. 취소 트랜잭션에 참여한다.
     * 환불 PG 호출이 실패해도 취소 자체는 성립 — FAILED/UNKNOWN 행이 재처리 대상으로 남는다
     * ("취소했는데 돈이 안 들어와요"를 기록 없이 만들지 않기 위한 구조).
     *
     * @return 산정된 환불액 (0이면 환불 행을 만들지 않는다)
     */
    @Transactional
    public int refundOnCancel(Reservation reservation, RefundCalculator.CancelCause cause) {
        Payment paid = paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                        reservation.getId(), PaymentType.PAY, PaymentStatus.APPROVED)
                .orElse(null);
        if (paid == null) {
            return 0;
        }

        int refundAmount = refundCalculator.calculate(
                policyOf(reservation).getRefundRule(),
                reservation.getSlot().getSlotDate(), LocalDate.now(),
                paid.getAmount(), cause);
        if (refundAmount == 0) {
            return 0;
        }
        issueRefund(reservation, paid, refundAmount);
        return refundAmount;
    }

    /**
     * S5: 방문 완료·노쇼 철회 시 예약금 전액 환불. 방문 확인 트랜잭션에 참여한다.
     */
    @Transactional
    public int refundDeposit(Reservation reservation) {
        Payment paid = paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                        reservation.getId(), PaymentType.PAY, PaymentStatus.APPROVED)
                .orElse(null);
        if (paid == null) {
            return 0;
        }
        issueRefund(reservation, paid, paid.getAmount());
        return paid.getAmount();
    }

    /**
     * FR-07: UNKNOWN 결제 재확인 (핵심영역 2의 후속 정합성).
     * PAY 건이 실제로는 승인돼 있었는데 예약이 이미 만료·취소됐다면 —
     * "돈은 나갔는데 예약은 없는" 상태 — 즉시 전액 환불 행을 만들어 스스로 복구한다 (S3).
     *
     * @return 상태가 확정(APPROVED/FAILED)됐으면 true, 여전히 미확정이면 false
     */
    @Transactional
    public boolean reconcile(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.UNKNOWN) {
            return false;   // 이미 다른 경로로 확정 — 배치 재실행 멱등
        }
        PgInquiryResult result;
        try {
            result = pgClient.inquire(payment.getIdempotencyKey());
        } catch (PgTimeoutException e) {
            return false;   // 재확인도 미응답 — UNKNOWN 유지, 다음 주기로
        }

        if (!result.approved()) {
            payment.fail();
            return true;
        }
        payment.approve(result.pgTxId());
        if (payment.getType() == PaymentType.REFUND) {
            markPaidRowAfterRefund(payment);
            return true;
        }
        Reservation reservation = reservationRepository
                .findWithLockById(payment.getReservation().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
            transition(reservation, ReservationStatus.CONFIRMED);
            publishConfirmed(reservation);
            log.info("UNKNOWN 재확인 — payment {} 승인 확인, 예약 {} CONFIRMED 복구", payment.getId(), reservation.getId());
        } else {
            issueRefund(reservation, payment, payment.getAmount());
            log.warn("UNKNOWN 재확인 — payment {} 승인 확인됐으나 예약 {}은 {} 상태. 전액 환불로 복구",
                    payment.getId(), reservation.getId(), reservation.getStatus());
        }
        return true;
    }

    /**
     * FR-09: 환불 실패 재처리. 같은 환불 사건의 재시도이므로 새 행을 만들지 않고
     * FAILED 행을 다시 몰아간다 ("새 행" 원칙은 새 키의 새 시도에 적용).
     *
     * @return 이번 시도로 승인됐으면 true
     */
    @Transactional
    public boolean retryRefund(Long refundPaymentId) {
        Payment refund = paymentRepository.findById(refundPaymentId).orElse(null);
        if (refund == null || refund.getType() != PaymentType.REFUND
                || refund.getStatus() != PaymentStatus.FAILED) {
            return false;
        }
        Payment paid = paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                        refund.getReservation().getId(), PaymentType.PAY, PaymentStatus.APPROVED)
                .orElse(null);
        if (paid == null) {
            log.error("환불 재처리 불가 — payment {}의 원 결제(APPROVED PAY)가 없음. 수동 확인 필요", refund.getId());
            return false;
        }
        try {
            String pgTxId = pgClient.cancel(paid.getPgTxId(), refund.getAmount());
            refund.approve(pgTxId);
            markPaidRowAfterRefund(refund);
            log.info("환불 재처리 성공 — payment {} ({}원)", refund.getId(), refund.getAmount());
            return true;
        } catch (PgDeclinedException e) {
            log.warn("환불 재처리 거절 — payment {} 다음 주기 재시도: {}", refund.getId(), e.getMessage());
            return false;
        } catch (PgTimeoutException e) {
            refund.markUnknown();   // 재확인(FR-07) 대상으로 이관
            return false;
        }
    }

    /**
     * 환불 사건 행 생성 + PG 취소 호출. 호출부 트랜잭션에 참여하며,
     * PG 실패는 예외로 전파하지 않고 FAILED/UNKNOWN 행으로 남긴다 (FR-09의 재료).
     */
    private void issueRefund(Reservation reservation, Payment paid, int refundAmount) {
        Payment refund = paymentRepository.save(Payment.builder()
                .reservation(reservation)
                .type(PaymentType.REFUND)
                .amount(refundAmount)
                .status(PaymentStatus.READY)
                .idempotencyKey("rf-" + paid.getId() + "-" + UUID.randomUUID())
                .createdAt(LocalDateTime.now())
                .build());
        try {
            String pgTxId = pgClient.cancel(paid.getPgTxId(), refundAmount);
            refund.approve(pgTxId);
            if (refundAmount == paid.getAmount()) {
                paid.cancel();
            } else {
                paid.partialCancel();
            }
        } catch (PgDeclinedException e) {
            refund.fail();
            log.warn("환불 PG 거절 — payment {} 재처리(FR-09) 대상: {}", refund.getId(), e.getMessage());
        } catch (PgTimeoutException e) {
            refund.markUnknown();
            log.warn("환불 PG 응답 유실 — payment {} 재확인 대상", refund.getId());
        }
    }

    // 환불 승인 확정 후 원 결제(PAY) 행에 취소 표시
    private void markPaidRowAfterRefund(Payment refund) {
        paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                        refund.getReservation().getId(), PaymentType.PAY, PaymentStatus.APPROVED)
                .ifPresent(paid -> {
                    if (refund.getAmount() == paid.getAmount()) {
                        paid.cancel();
                    } else {
                        paid.partialCancel();
                    }
                });
    }

    /**
     * S4: 취소 전 환불액 사전 고지. 취소 확정 시의 refundOnCancel과 같은 계산기·같은 규칙을 쓴다 —
     * 고지액과 실제 환불액이 다르면 그 자체가 분쟁이 되므로, 계산 경로를 하나로 유지한다.
     * 결제 전(PENDING_PAYMENT 등)이면 결제액 0원 → 환불액 0원으로 응답한다.
     */
    public RefundPreviewResponseDto previewRefund(Reservation reservation) {
        Payment paid = paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                        reservation.getId(), PaymentType.PAY, PaymentStatus.APPROVED)
                .orElse(null);
        String refundRule = policyOf(reservation).getRefundRule();
        LocalDate visitDate = reservation.getSlot().getSlotDate();
        LocalDate today = LocalDate.now();
        int paidAmount = paid == null ? 0 : paid.getAmount();
        int rate = refundCalculator.rate(refundRule, visitDate, today, RefundCalculator.CancelCause.USER);
        int refundAmount = refundCalculator.calculate(refundRule, visitDate, today,
                paidAmount, RefundCalculator.CancelCause.USER);
        return new RefundPreviewResponseDto(
                reservation.getId(),
                reservation.getStatus(),
                reservation.getStatus().canTransitionTo(ReservationStatus.CANCELED_BY_USER),
                paidAmount, rate, refundAmount,
                ChronoUnit.DAYS.between(today, visitDate), visitDate, refundRule);
    }

    /**
     * 예약별 결제 이력 조회 (사건 행이 쌓인 순서의 역순).
     */
    public List<PaymentResponseDto> getPayments(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_RESERVATION_OWNER);
        }
        return paymentRepository.findAllByReservationIdOrderByIdDesc(reservationId).stream()
                .map(PaymentResponseDto::from)
                .toList();
    }

    private ReservationPolicy policyOf(Reservation reservation) {
        return reservationPolicyRepository
                .findByRestaurantId(reservation.getSlot().getRestaurant().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));
    }

    // ReservationService를 주입하면 취소 흐름과 순환 참조가 생겨 enum의 전이 규칙을 직접 쓴다
    private void transition(Reservation reservation, ReservationStatus target) {
        if (!reservation.getStatus().canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        reservation.changeStatus(target);
    }

    private void publishConfirmed(Reservation reservation) {
        eventPublisher.publishEvent(NotificationEvent.of(reservation.getMember().getId(),
                NotificationType.RESERVATION_CONFIRMED,
                "%s %s %s 예약이 확정되었습니다.".formatted(
                        reservation.getSlot().getRestaurant().getName(),
                        reservation.getSlot().getSlotDate(),
                        reservation.getSlot().getSlotTime())));
    }
}
