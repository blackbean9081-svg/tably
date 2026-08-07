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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
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
        Set<DayOfWeek> closedDays = parseClosedDays(policy.getClosedDays());

        List<Slot> slots = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (closedDays.contains(date.getDayOfWeek())) {
                continue;
            }
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

    @Transactional
    public int openDueMonthlySlots(LocalDateTime now) {
        int opened = 0;
        for (ReservationPolicy policy : policyRepository.findAll()) {
            try {
                if (!isOpenDue(policy.getOpenRule(), now)) {
                    continue;
                }
                Restaurant restaurant = policy.getRestaurant();
                YearMonth target = YearMonth.from(now).plusMonths(1);
                if (slotRepository.existsByRestaurantIdAndSlotDateBetween(
                        restaurant.getId(), target.atDay(1), target.atEndOfMonth())) {
                    continue;
                }
                int count = generateMonthlySlots(restaurant.getOwner().getId(), restaurant.getId(),
                        new SlotGenerateRequestDto(target.toString()));
                log.info("슬롯 자동 오픈 — {} {}월분 {}개", restaurant.getName(), target, count);
                opened++;
            } catch (Exception e) {
                log.warn("슬롯 자동 오픈 실패 — policy {}: {}", policy.getId(), e.getMessage());
            }
        }
        return opened;
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

    private Set<DayOfWeek> parseClosedDays(String closedDays) {
        if (closedDays == null || closedDays.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(closedDays.split(","))
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
    }

    private boolean isOpenDue(String openRule, LocalDateTime now) {
        String[] parts = openRule.split(":", 3);
        if (parts.length != 3 || !"MONTHLY".equals(parts[0])) {
            return false;
        }
        YearMonth month = YearMonth.from(now);
        int day = Math.min(Integer.parseInt(parts[1]), month.lengthOfMonth());
        return !now.isBefore(month.atDay(day).atTime(LocalTime.parse(parts[2])));
    }
}
