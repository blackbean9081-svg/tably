package com.app.tably.reservation.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.notification.entity.NotificationType;
import com.app.tably.notification.event.NotificationEvent;
import com.app.tably.payment.service.PaymentService;
import com.app.tably.payment.service.RefundCalculator;
import com.app.tably.reservation.dto.AppealRequestDto;
import com.app.tably.reservation.dto.AppealResponseDto;
import com.app.tably.reservation.dto.RefundPreviewResponseDto;
import com.app.tably.reservation.dto.ReservationHoldRequestDto;
import com.app.tably.reservation.dto.ReservationResponseDto;
import com.app.tably.reservation.entity.AppealStatus;
import com.app.tably.reservation.entity.NoShowAppeal;
import com.app.tably.reservation.entity.Reservation;
import com.app.tably.reservation.entity.ReservationStatus;
import com.app.tably.reservation.repository.NoShowAppealRepository;
import com.app.tably.reservation.repository.ReservationRepository;
import com.app.tably.slot.entity.Slot;
import com.app.tably.slot.entity.SlotStatus;
import com.app.tably.slot.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    // 선점 후 결제 시한 (S2: "예약금 결제를 10분 안에 완료해주세요")
    public static final Duration HOLD_TIMEOUT = Duration.ofMinutes(10);

    private final ReservationRepository reservationRepository;
    private final NoShowAppealRepository noShowAppealRepository;
    private final SlotRepository slotRepository;
    private final MemberRepository memberRepository;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;
    private final SlotHoldGate slotHoldGate;

    /*
     * TODO [핵심영역 1 — 슬롯 선점 (락)] 개발자 본인이 구현할 것. Claude Code 구현 금지.
     *
     * 이 메서드가 만족해야 할 조건:
     *  1. 같은 슬롯에 동시 요청 N명 중 정확히 1명만 성공한다 — 중복 예약 0건은 타협 불가 (S2)
     *  2. 실패한 요청은 즉시 SLOT_ALREADY_TAKEN(409, "방금 마감되었습니다")을 받는다
     *     — 락 대기로 서버 스레드를 오래 점유하지 않을 것 (실패 응답도 빨라야 한다)
     *  3. slot.status == CLOSED 이면 SLOT_CLOSED
     *  4. 해당 슬롯에 활성 예약(PENDING_PAYMENT, CONFIRMED)이 이미 있으면 실패
     *     — 단, "조회 후 저장" 사이의 틈(check-then-act)을 락 또는 제약으로 막아야 한다
     *  5. 성공 시 reservation 1행 생성: status=PENDING_PAYMENT, held_at=now → id 반환
     *  6. EXPIRED/CANCELED 예약이 있던 슬롯은 다시 선점 가능해야 한다
     *
     * 접근 후보 (트레이드오프 비교 후 선택, k6로 검증):
     *  (a) 비관적 락: slot 행 SELECT ... FOR UPDATE 후 활성 예약 확인
     *  (b) DB 부분 유니크 제약: 활성 상태의 slot_id에만 유니크 인덱스 → 저장 시점 충돌 감지
     *  (c) 조건부 UPDATE로 선점 표시 후 예약 생성
     */
    @Transactional
    public Long hold(Long memberId, ReservationHoldRequestDto request) {
        // 2막: Redis 게이트(mode=redis)면 패자는 DB에 닿기 전에 여기서 즉시 409.
        // 1막(mode=db)은 무조건 통과 — 아래 DB 락 경로가 그대로 기준선이 된다.
        if (!slotHoldGate.tryAcquire(request.slotId(), memberId)) {
            throw new BusinessException(ErrorCode.SLOT_ALREADY_TAKEN);
        }
        try {
            Slot slot;
            try {
                slot = slotRepository.findWithLockById(request.slotId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));

                if (slot.getStatus() == SlotStatus.CLOSED) {
                    throw new BusinessException(ErrorCode.SLOT_CLOSED);
                }
            } catch (PessimisticLockingFailureException e) {
                throw new BusinessException(ErrorCode.SLOT_ALREADY_TAKEN);
            }

            boolean taken = reservationRepository.existsBySlotIdAndStatusIn(slot.getId(),
                    List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED));

            if (taken) {
                throw new BusinessException(ErrorCode.SLOT_ALREADY_TAKEN);
            }

            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

            Reservation reservation = Reservation.builder()
                    .slot(slot)
                    .member(member)
                    .partySize(request.partySize())
                    .status(ReservationStatus.PENDING_PAYMENT)
                    .heldAt(LocalDateTime.now())
                    .build();

            Reservation saved = reservationRepository.save(reservation);

            return saved.getId();
        } catch (RuntimeException e) {
            // 게이트만 잡고 예약을 못 만든 채 끝나면 슬롯이 TTL(10분)까지 헛묶인다 — 즉시 되돌린다.
            // (커밋 자체가 실패하는 드문 경우는 되돌리지 못하지만 TTL이 수습한다)
            slotHoldGate.release(request.slotId());
            throw e;
        }
    }

    /**
     * 핵심영역 4 — 상태 머신의 단일 관문. 허용 전이 목록은 ReservationStatus가 갖는다.
     * 경합(만료 배치 vs 결제 완료, 노쇼 배치 vs 방문 처리)은 검증만으로 못 막으므로
     * 호출부가 행 락(findWithLockById) 또는 조건부 UPDATE(updateStatusIfCurrent)와 함께 쓴다.
     */
    public void validateTransition(ReservationStatus current, ReservationStatus target) {
        if (!current.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "%s → %s 전이는 허용되지 않습니다".formatted(current, target));
        }
    }

    public List<ReservationResponseDto> getMyReservations(Long memberId) {
        return reservationRepository.findAllByMemberIdOrderByIdDesc(memberId).stream()
                .map(ReservationResponseDto::from)
                .toList();
    }

    public ReservationResponseDto getReservation(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_RESERVATION_OWNER);
        }
        return ReservationResponseDto.from(reservation);
    }

    /**
     * S4: 취소 전 환불액 사전 고지 — "지금 취소하면 환불액 N원" 확인 화면의 근거.
     * 계산은 취소 확정 경로와 동일한 계산기를 쓰는 PaymentService에 위임한다.
     */
    public RefundPreviewResponseDto getRefundPreview(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_RESERVATION_OWNER);
        }
        return paymentService.previewRefund(reservation);
    }

    /**
     * S4: 손님 취소. 행 락으로 결제 승인·노쇼 배치·동시 취소와 직렬화한 뒤
     * 전이 검증 → 상태 변경 → 시점별 환불 기록(FR-08)까지 한 트랜잭션으로 처리한다.
     */
    @Transactional
    public void cancelByUser(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findWithLockById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_RESERVATION_OWNER);
        }
        validateTransition(reservation.getStatus(), ReservationStatus.CANCELED_BY_USER);
        reservation.changeStatus(ReservationStatus.CANCELED_BY_USER);
        // 취소된 슬롯은 다시 예약 가능해야 한다 (S4) — TTL이 남은 게이트를 즉시 되돌린다
        slotHoldGate.release(reservation.getSlot().getId());
        int refunded = paymentService.refundOnCancel(reservation, RefundCalculator.CancelCause.USER);
        eventPublisher.publishEvent(NotificationEvent.of(memberId, NotificationType.RESERVATION_CANCELED,
                "%s 예약이 취소되었습니다. 환불 예정액 %,d원.".formatted(describe(reservation), refunded)));
    }

    /**
     * FR-05: 선점 10분 만료 처리. 조건부 일괄 UPDATE 한 문장 —
     * 결제 완료(행 락 보유)와 경합하면 락 해제까지 대기 후 조건 불일치로 비켜 가므로
     * "둘 중 한쪽만 이긴다"가 DB 수준에서 보장된다.
     */
    @Transactional
    public int expireOverdueHolds() {
        validateTransition(ReservationStatus.PENDING_PAYMENT, ReservationStatus.EXPIRED);
        LocalDateTime threshold = LocalDateTime.now().minus(HOLD_TIMEOUT);
        int expired = reservationRepository.updateStatusAllHeldBefore(
                ReservationStatus.PENDING_PAYMENT, threshold, ReservationStatus.EXPIRED);
        if (expired > 0) {
            log.info("선점 만료 {}건 처리", expired);
        }
        return expired;
    }

    /**
     * S8/FR-10: 식당 귀책 취소 — 휴업 일괄 취소가 건별 트랜잭션으로 호출한다.
     * 귀책이 식당이므로 환불 정책을 무시하고 전액 환불된다.
     */
    @Transactional
    public void cancelByShop(Long reservationId) {
        Reservation reservation = reservationRepository.findWithLockById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        validateTransition(reservation.getStatus(), ReservationStatus.CANCELED_BY_SHOP);
        reservation.changeStatus(ReservationStatus.CANCELED_BY_SHOP);
        slotHoldGate.release(reservation.getSlot().getId());
        paymentService.refundOnCancel(reservation, RefundCalculator.CancelCause.SHOP);
        eventPublisher.publishEvent(NotificationEvent.of(reservation.getMember().getId(),
                NotificationType.RESERVATION_CANCELED,
                "식당 사정으로 %s 예약이 취소되었습니다. 예약금 전액이 환불됩니다.".formatted(describe(reservation))));
    }

    /**
     * FR-11: 사장의 방문 완료 처리 + 노쇼 당일 정정 (S5).
     * CONFIRMED→VISITED(정상 방문), NO_SHOW→VISITED(정정 — 당일 자정까지만).
     * 방문 확인 = 예약금 전액 환불. 행 락으로 노쇼 배치와 직렬화한다.
     */
    @Transactional
    public void markVisited(Long ownerId, Long reservationId) {
        Reservation reservation = reservationRepository.findWithLockById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.getSlot().getRestaurant().isOwnedBy(ownerId)) {
            throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
        }
        ReservationStatus current = reservation.getStatus();
        validateTransition(current, ReservationStatus.VISITED);
        if (current == ReservationStatus.NO_SHOW
                && LocalDate.now().isAfter(reservation.getSlot().getSlotDate())) {
            // 자정 이후의 번복은 이의신청 → 운영자 심사 경로로만 (S5)
            throw new BusinessException(ErrorCode.NO_SHOW_CORRECTION_EXPIRED);
        }
        reservation.changeStatus(ReservationStatus.VISITED);
        paymentService.refundDeposit(reservation);
    }

    /**
     * FR-12: 단건 노쇼 전환 — 노쇼 배치가 건별 트랜잭션으로 호출한다.
     * 배치가 읽은 뒤 사장이 방문 처리한 예약을 NO_SHOW로 덮어쓰는 사고(S5)를
     * 조건부 UPDATE로 차단 — 0건이면 경합에서 진 것이므로 false.
     */
    @Transactional
    public boolean markNoShow(Long reservationId) {
        validateTransition(ReservationStatus.CONFIRMED, ReservationStatus.NO_SHOW);
        boolean marked = reservationRepository.updateStatusIfCurrent(
                reservationId, ReservationStatus.CONFIRMED, ReservationStatus.NO_SHOW) == 1;
        if (marked) {
            reservationRepository.findById(reservationId).ifPresent(reservation ->
                    eventPublisher.publishEvent(NotificationEvent.of(reservation.getMember().getId(),
                            NotificationType.NO_SHOW_MARKED,
                            "%s 예약이 미방문으로 노쇼 처리되었습니다. 이의가 있으면 이의신청해주세요.".formatted(describe(reservation)))));
        }
        return marked;
    }

    // ── FR-13: 노쇼 이의신청 (S5) ────────────────────────────────────────

    @Transactional
    public AppealResponseDto fileNoShowAppeal(Long memberId, Long reservationId, AppealRequestDto request) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_RESERVATION_OWNER);
        }
        if (reservation.getStatus() != ReservationStatus.NO_SHOW) {
            throw new BusinessException(ErrorCode.APPEAL_NOT_ALLOWED);
        }
        if (noShowAppealRepository.existsByReservationIdAndStatus(reservationId, AppealStatus.OPEN)) {
            throw new BusinessException(ErrorCode.ALREADY_APPEALED);
        }
        NoShowAppeal appeal = noShowAppealRepository.save(NoShowAppeal.builder()
                .reservation(reservation)
                .member(reservation.getMember())
                .reason(request.reason())
                .status(AppealStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
        return AppealResponseDto.from(appeal);
    }

    public List<AppealResponseDto> getOpenAppeals() {
        return noShowAppealRepository.findAllByStatusOrderByIdAsc(AppealStatus.OPEN).stream()
                .map(AppealResponseDto::from)
                .toList();
    }

    /**
     * 이의신청 인용 — NO_SHOW → NO_SHOW_REVOKED + 예약금 환불.
     * 이미 몰수가 정산에 반영된 뒤라면 다음 회차에서 차감(FR-19)한다 — 정산(P2) 도입 시 이 지점에 조정 기록 연결.
     */
    @Transactional
    public AppealResponseDto acceptAppeal(Long appealId) {
        NoShowAppeal appeal = getOpenAppeal(appealId);
        Reservation reservation = reservationRepository.findWithLockById(appeal.getReservation().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        validateTransition(reservation.getStatus(), ReservationStatus.NO_SHOW_REVOKED);
        reservation.changeStatus(ReservationStatus.NO_SHOW_REVOKED);
        paymentService.refundDeposit(reservation);
        appeal.accept(LocalDateTime.now());
        eventPublisher.publishEvent(NotificationEvent.of(reservation.getMember().getId(),
                NotificationType.NO_SHOW_REVOKED,
                "%s 노쇼 이의신청이 인용되었습니다. 예약금이 환불됩니다.".formatted(describe(reservation))));
        return AppealResponseDto.from(appeal);
    }

    @Transactional
    public AppealResponseDto rejectAppeal(Long appealId) {
        NoShowAppeal appeal = getOpenAppeal(appealId);
        appeal.reject(LocalDateTime.now());
        return AppealResponseDto.from(appeal);
    }

    private NoShowAppeal getOpenAppeal(Long appealId) {
        NoShowAppeal appeal = noShowAppealRepository.findById(appealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPEAL_NOT_FOUND));
        if (!appeal.isOpen()) {
            throw new BusinessException(ErrorCode.APPEAL_ALREADY_DECIDED);
        }
        return appeal;
    }

    private String describe(Reservation reservation) {
        return "%s %s %s".formatted(
                reservation.getSlot().getRestaurant().getName(),
                reservation.getSlot().getSlotDate(),
                reservation.getSlot().getSlotTime());
    }
}
