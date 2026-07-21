package com.app.tably.slot.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.restaurant.entity.ReservationPolicy;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.ReservationPolicyRepository;
import com.app.tably.restaurant.repository.RestaurantRepository;
import com.app.tably.slot.dto.SlotGenerateRequestDto;
import com.app.tably.slot.dto.SlotResponseDto;
import com.app.tably.slot.entity.Slot;
import com.app.tably.slot.entity.SlotStatus;
import com.app.tably.slot.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SlotService {

    private final SlotRepository slotRepository;
    private final RestaurantRepository restaurantRepository;
    private final ReservationPolicyRepository policyRepository;

    /**
     * FR-02: 정책 기반 해당 월 슬롯 일괄 생성 (일수 × 운영타임 × 테이블수).
     * 같은 월 재실행은 SLOT_ALREADY_GENERATED — 슬롯 테이블의 유니크 제약이 최후 방어선.
     */
    @Transactional
    public int generateMonthlySlots(Long ownerId, Long restaurantId, SlotGenerateRequestDto request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        if (!restaurant.isOwnedBy(ownerId)) {
            throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
        }
        ReservationPolicy policy = policyRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));

        YearMonth yearMonth = YearMonth.parse(request.yearMonth());
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        if (slotRepository.existsByRestaurantIdAndSlotDateBetween(restaurantId, start, end)) {
            throw new BusinessException(ErrorCode.SLOT_ALREADY_GENERATED);
        }

        List<LocalTime> times = Arrays.stream(policy.getSlotTimes().split(","))
                .map(LocalTime::parse)
                .toList();

        List<Slot> slots = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            for (LocalTime time : times) {
                for (int tableNo = 1; tableNo <= policy.getTablesPerTime(); tableNo++) {
                    slots.add(Slot.builder()
                            .restaurant(restaurant)
                            .slotDate(date)
                            .slotTime(time)
                            .tableNo(tableNo)
                            .status(SlotStatus.OPEN)
                            .build());
                }
            }
        }
        slotRepository.saveAll(slots);
        return slots.size();
    }

    /**
     * FR-03: 날짜별 슬롯 조회. 오픈런 때 가장 잦은 호출 — 예약 여부 표시는 예약 도메인 합류 시 확장.
     */
    public List<SlotResponseDto> getSlots(Long restaurantId, LocalDate date) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND);
        }
        return slotRepository.findAllByRestaurantIdAndSlotDateOrderBySlotTimeAscTableNoAsc(restaurantId, date)
                .stream()
                .map(SlotResponseDto::from)
                .toList();
    }
}
