package com.app.tably.settlement.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.payment.entity.Payment;
import com.app.tably.payment.entity.PaymentStatus;
import com.app.tably.payment.entity.PaymentType;
import com.app.tably.payment.repository.PaymentRepository;
import com.app.tably.reservation.entity.Reservation;
import com.app.tably.reservation.entity.ReservationStatus;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.RestaurantRepository;
import com.app.tably.settlement.dto.SettlementItemDto;
import com.app.tably.settlement.dto.SettlementResponseDto;
import com.app.tably.settlement.entity.Settlement;
import com.app.tably.settlement.entity.SettlementItem;
import com.app.tably.settlement.entity.SettlementItemType;
import com.app.tably.settlement.repository.SettlementItemRepository;
import com.app.tably.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    // 노쇼 위약금 중 플랫폼 수수료 비율(%) — 비즈니스 모델의 수익원
    public static final int COMMISSION_RATE = 10;

    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final PaymentRepository paymentRepository;
    private final RestaurantRepository restaurantRepository;
    private final MemberRepository memberRepository;

    /**
     * FR-17: 식당 한 곳의 월 정산 — 식당 단위 트랜잭션.
     * 이미 정산된 달은 그대로 반환(중복 지급 없음), 대상 건이 없으면 정산서를 만들지 않는다.
     * 몰수 후보는 "아직 item으로 안 잡힌 건"만 조회하므로 재실행이 안전하고,
     * (reservation, type) 유니크 제약이 최후의 방어선이다.
     */
    @Transactional
    public Optional<Settlement> settleMonth(Long restaurantId, YearMonth month) {
        String monthKey = month.toString();
        Optional<Settlement> existing = settlementRepository
                .findByRestaurantIdAndSettlementMonth(restaurantId, monthKey);
        if (existing.isPresent()) {
            return existing;
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        List<Reservation> forfeits = settlementItemRepository.findForfeitCandidates(
                restaurantId, start, end, ReservationStatus.NO_SHOW, SettlementItemType.FORFEIT);
        List<Reservation> revokes = settlementItemRepository.findRevokeCandidates(
                restaurantId, ReservationStatus.NO_SHOW_REVOKED,
                SettlementItemType.FORFEIT, SettlementItemType.REVOKE_ADJUSTMENT);
        if (forfeits.isEmpty() && revokes.isEmpty()) {
            return Optional.empty();
        }

        record Draft(Reservation reservation, SettlementItemType type, int amount) {
        }
        List<Draft> drafts = new ArrayList<>();
        int totalForfeit = 0;
        int totalCommission = 0;
        int totalAdjustment = 0;

        for (Reservation reservation : forfeits) {
            Payment paid = paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                            reservation.getId(), PaymentType.PAY, PaymentStatus.APPROVED)
                    .orElse(null);
            if (paid == null) {
                // NO_SHOW인데 승인 결제가 없는 건 데이터 이상 — 정산에서 빼고 드러낸다 (조용한 0원 금지)
                log.error("정산 제외 — 노쇼 예약 {}에 승인된 결제가 없음. 수동 확인 필요", reservation.getId());
                continue;
            }
            drafts.add(new Draft(reservation, SettlementItemType.FORFEIT, paid.getAmount()));
            totalForfeit += paid.getAmount();
            totalCommission += commissionOf(paid.getAmount());
        }
        for (Reservation reservation : revokes) {
            SettlementItem forfeitItem = settlementItemRepository
                    .findByReservationIdAndType(reservation.getId(), SettlementItemType.FORFEIT)
                    .orElseThrow();   // 후보 쿼리가 존재를 보장
            // 철회 차감액 = 그 건으로 이전 회차에 지급된 몫 (몰수액 - 수수료). 수수료는 심사 비용으로 플랫폼이 유지
            int adjustment = -(forfeitItem.getAmount() - commissionOf(forfeitItem.getAmount()));
            drafts.add(new Draft(reservation, SettlementItemType.REVOKE_ADJUSTMENT, adjustment));
            totalAdjustment += adjustment;
        }
        if (drafts.isEmpty()) {
            return Optional.empty();
        }

        Settlement settlement = settlementRepository.save(Settlement.builder()
                .restaurant(restaurant)
                .settlementMonth(monthKey)
                .totalForfeit(totalForfeit)
                .totalCommission(totalCommission)
                .totalAdjustment(totalAdjustment)
                .payout(totalForfeit - totalCommission + totalAdjustment)
                .createdAt(LocalDateTime.now())
                .build());
        settlementItemRepository.saveAll(drafts.stream()
                .map(draft -> SettlementItem.builder()
                        .settlement(settlement)
                        .reservation(draft.reservation())
                        .type(draft.type())
                        .amount(draft.amount())
                        .build())
                .toList());
        log.info("정산 확정 — 식당 {} {}월: 몰수 {}건 {}원, 수수료 {}원, 차감 {}원 → 지급 {}원",
                restaurantId, monthKey, forfeits.size(), totalForfeit,
                totalCommission, totalAdjustment, settlement.getPayout());
        return Optional.of(settlement);
    }

    /**
     * FR-18: 정산서 조회 — 합계 + 건별 내역. 사장 본인 또는 운영자만.
     */
    public SettlementResponseDto getStatement(Long requesterId, Long restaurantId, String month) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        Member requester = memberRepository.findById(requesterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (requester.getRole() != Role.ADMIN && !restaurant.isOwnedBy(requesterId)) {
            throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
        }
        Settlement settlement = settlementRepository
                .findByRestaurantIdAndSettlementMonth(restaurantId, parseMonth(month).toString())
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        List<SettlementItemDto> items = settlementItemRepository
                .findAllBySettlementIdOrderByIdAsc(settlement.getId()).stream()
                .map(SettlementItemDto::from)
                .toList();
        return SettlementResponseDto.of(settlement, items);
    }

    public static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_MONTH);
        }
    }

    public static int commissionOf(int amount) {
        return amount * COMMISSION_RATE / 100;
    }
}
