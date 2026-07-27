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
import com.app.tably.restaurant.dto.ClosureResultDto;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.RestaurantRepository;
import com.app.tably.slot.entity.SlotStatus;
import com.app.tably.slot.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantClosureService {

    private final RestaurantRepository restaurantRepository;
    private final MemberRepository memberRepository;
    private final SlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    /**
     * S8/FR-10: 기간 휴업 — 슬롯 닫기 + 확정 예약 일괄 취소(전액 환불).
     *
     * 의도적으로 전체 트랜잭션을 걸지 않는다: 80건 중 3건이 실패해도
     * 나머지 77건이 롤백되면 안 되므로(S8), 취소는 건별 트랜잭션(cancelByShop)로 돌고
     * 실패 건만 모아 보고한다. PENDING_PAYMENT 선점 건은 취소 대상이 아니다 —
     * 슬롯이 닫혀 새 선점이 막히고, 미결제 선점은 10분 만료 배치가 정리한다.
     */
    public ClosureResultDto closeTemporarily(Long requesterId, Long restaurantId, ClosureRequestDto request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessException(ErrorCode.INVALID_CLOSURE_PERIOD);
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        Member requester = memberRepository.findById(requesterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (requester.getRole() != Role.ADMIN && !restaurant.isOwnedBy(requesterId)) {
            throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
        }

        int slotsClosed = slotRepository.updateStatusAllBetween(
                restaurantId, request.startDate(), request.endDate(), SlotStatus.OPEN, SlotStatus.CLOSED);

        List<Long> targets = reservationRepository.findIdsByRestaurantAndSlotDateBetweenAndStatus(
                restaurantId, request.startDate(), request.endDate(), ReservationStatus.CONFIRMED);

        List<Long> failed = new ArrayList<>();
        for (Long reservationId : targets) {
            try {
                reservationService.cancelByShop(reservationId);
            } catch (Exception e) {
                failed.add(reservationId);
                log.error("휴업 취소 실패 — reservation {} (재처리 필요)", reservationId, e);
            }
        }
        log.info("휴업 처리 — 식당 {}, {}~{}: 슬롯 {}개 닫힘, 예약 {}건 중 취소 {}건, 실패 {}건",
                restaurantId, request.startDate(), request.endDate(),
                slotsClosed, targets.size(), targets.size() - failed.size(), failed.size());
        return new ClosureResultDto(slotsClosed, targets.size(), targets.size() - failed.size(), failed);
    }
}
