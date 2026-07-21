package com.app.tably.payment.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.payment.dto.PaymentApproveRequestDto;
import com.app.tably.payment.dto.PaymentResponseDto;
import com.app.tably.payment.repository.PaymentRepository;
import com.app.tably.reservation.entity.Reservation;
import com.app.tably.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;

    /*
     * TODO [핵심영역 2 — 결제 멱등성 처리] 개발자 본인이 구현할 것. Claude Code 구현 금지.
     *
     * 이 메서드가 만족해야 할 조건 (S3, FR-06/07):
     *  1. 같은 idempotency_key 재요청은 새 청구 없이 "기존 결과"를 그대로 반환한다
     *     — 버튼 2번 클릭 = 청구 1번. 첫 요청이 아직 처리 중일 때 두 번째가 오는 경합도 고려
     *     (유니크 제약 충돌을 잡아서 기존 행을 조회하는 방식 등)
     *  2. 결제 시도 1번 = payment 1행 (READY로 생성 → 결과에 따라 상태만 갱신, 행 재사용 금지)
     *  3. 대상 예약이 PENDING_PAYMENT 상태가 아니면 INVALID_STATUS_TRANSITION
     *  4. held_at + 10분이 지난 예약이면 PAYMENT_TIME_EXPIRED (만료 배치와의 경합 고려)
     *  5. 요청 금액이 (정책 예약금 × 인원수)와 다르면 PAYMENT_AMOUNT_MISMATCH
     *  6. PG 응답 시나리오별 상태 기록:
     *     승인 → APPROVED + pg_tx_id 저장 + 예약 CONFIRMED 전이(핵심영역 4 경유)
     *     거절 → FAILED (예약은 PENDING_PAYMENT 유지 — 재시도 가능)
     *     타임아웃/무응답 → UNKNOWN 으로 기록하고 "실패로 단정하지 않는다"
     *       → 별도 재확인(FR-07)이 PG에 승인 여부를 물어 정합성을 맞춘다
     *  7. 예약 상태 전이와 결제 행 기록은 하나의 트랜잭션 경계 안에서 정합성이 깨지지 않아야 한다
     */
    @Transactional
    public PaymentResponseDto approve(Long memberId, PaymentApproveRequestDto request) {
        throw new UnsupportedOperationException("핵심영역 2 — 결제 멱등성 처리 미구현");
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

    // FR-07(UNKNOWN 재확인)·FR-09(환불 실패 재처리)는 PG 클라이언트(토스페이먼츠 테스트) 연동 시
    // 이 서비스에 배치 진입점을 추가한다 — findAllByStatus(UNKNOWN/FAILED)가 조회 재료
}
