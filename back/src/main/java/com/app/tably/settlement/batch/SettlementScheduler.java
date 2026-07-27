package com.app.tably.settlement.batch;

import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.RestaurantRepository;
import com.app.tably.settlement.dto.SettlementRunResultDto;
import com.app.tably.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementScheduler {

    private final RestaurantRepository restaurantRepository;
    private final SettlementService settlementService;

    // S7: 매월 1일 새벽 4시, 전월분 정산
    @Scheduled(cron = "0 0 4 1 * *")
    public void settlePreviousMonth() {
        runFor(YearMonth.now().minusMonths(1));
    }

    /**
     * FR-17: 식당별 독립 트랜잭션으로 순회 — 한 식당의 실패가 다른 식당 지급을 막지 않고,
     * 실패 식당은 재실행 시 이어서 처리된다 (settleMonth가 멱등이므로 안전).
     * 운영자 수동 실행(/api/admin/settlements/run)도 이 진입점을 쓴다.
     */
    public SettlementRunResultDto runFor(YearMonth month) {
        List<Restaurant> restaurants = restaurantRepository.findAll();
        int settled = 0;
        int skipped = 0;
        int failed = 0;
        for (Restaurant restaurant : restaurants) {
            try {
                if (settlementService.settleMonth(restaurant.getId(), month).isPresent()) {
                    settled++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.error("정산 실패 — 식당 {} {}월 (재실행 대상)", restaurant.getId(), month, e);
            }
        }
        log.info("정산 배치 {}월: 식당 {}곳 중 정산 {}건, 대상 없음 {}건, 실패 {}건",
                month, restaurants.size(), settled, skipped, failed);
        return new SettlementRunResultDto(month.toString(), settled, skipped, failed);
    }
}
