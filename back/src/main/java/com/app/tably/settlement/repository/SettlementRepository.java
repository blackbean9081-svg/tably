package com.app.tably.settlement.repository;

import com.app.tably.settlement.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByRestaurantIdAndSettlementMonth(Long restaurantId, String settlementMonth);
}
