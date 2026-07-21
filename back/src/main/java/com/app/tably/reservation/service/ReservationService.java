package com.app.tably.reservation.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.reservation.dto.ReservationHoldRequestDto;
import com.app.tably.reservation.dto.ReservationResponseDto;
import com.app.tably.reservation.entity.Reservation;
import com.app.tably.reservation.entity.ReservationStatus;
import com.app.tably.reservation.repository.ReservationRepository;
import com.app.tably.slot.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    // 선점 후 결제 시한 (S2: "예약금 결제를 10분 안에 완료해주세요")
    public static final Duration HOLD_TIMEOUT = Duration.ofMinutes(10);

    private final ReservationRepository reservationRepository;
    private final SlotRepository slotRepository;

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
        throw new UnsupportedOperationException("핵심영역 1 — 슬롯 선점 로직 미구현");
    }

    /*
     * TODO [핵심영역 4 — 예약 상태 전이 검증] 개발자 본인이 구현할 것. Claude Code 구현 금지.
     *
     * 이 메서드가 만족해야 할 조건 (상태 머신: requirements.md 4장 / erd.md):
     *  1. 허용 전이만 통과, 그 외에는 BusinessException(INVALID_STATUS_TRANSITION):
     *     PENDING_PAYMENT → CONFIRMED(결제 완료) | EXPIRED(10분 미결제)
     *     CONFIRMED      → CANCELED_BY_USER | CANCELED_BY_SHOP | VISITED | NO_SHOW
     *     NO_SHOW        → VISITED(당일 내 사장 정정) | NO_SHOW_REVOKED(이의신청 인용)
     *     그 외 상태는 종결 상태 — 어떤 전이도 불가
     *  2. 경합 상황에서 한쪽만 이겨야 한다:
     *     예) 만료 배치의 PENDING_PAYMENT→EXPIRED 와 결제 완료의 →CONFIRMED 가 동시에 오면
     *     둘 중 하나만 반영 (낙관적 락 또는 조건부 UPDATE 고려)
     *  3. NO_SHOW → VISITED 정정은 "당일 자정까지"라는 시간 조건이 붙는다 (S5)
     */
    public void validateTransition(ReservationStatus current, ReservationStatus target) {
        throw new UnsupportedOperationException("핵심영역 4 — 상태 전이 검증 미구현");
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
     * 손님 취소. 조회·권한 검증까지만 구현 —
     * 상태 전이는 핵심영역 4, 환불 계산은 핵심영역 3(PaymentService 쪽) 구현 후 연결된다.
     */
    @Transactional
    public void cancelByUser(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_RESERVATION_OWNER);
        }
        validateTransition(reservation.getStatus(), ReservationStatus.CANCELED_BY_USER);
        reservation.changeStatus(ReservationStatus.CANCELED_BY_USER);
        // TODO 환불 계산(핵심영역 3) 구현 후: 시점별 환불액 산정 → PaymentService 환불 요청 연결 (S4)
    }

    /**
     * FR-05: 선점 10분 만료 처리. 대상 조회는 구현 —
     * 실제 EXPIRED 전이는 상태 전이 검증(핵심영역 4) 구현 후 활성화한다.
     */
    @Transactional
    public int expireOverdueHolds() {
        LocalDateTime threshold = LocalDateTime.now().minus(HOLD_TIMEOUT);
        List<Reservation> overdue = reservationRepository
                .findAllByStatusAndHeldAtBefore(ReservationStatus.PENDING_PAYMENT, threshold);
        if (overdue.isEmpty()) {
            return 0;
        }
        // TODO 핵심영역 4 구현 후 아래 주석 해제 — 결제 완료와의 경합에서 한쪽만 이겨야 함
        // overdue.forEach(r -> {
        //     validateTransition(r.getStatus(), ReservationStatus.EXPIRED);
        //     r.changeStatus(ReservationStatus.EXPIRED);
        // });
        log.warn("선점 만료 대상 {}건 발견 — 상태 전이 검증(핵심영역 4) 구현 전이라 전이 보류", overdue.size());
        return overdue.size();
    }
}
