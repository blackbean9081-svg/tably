package com.app.tably.waiting.repository;

import com.app.tably.waiting.entity.WaitingCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WaitingCounterRepository extends JpaRepository<WaitingCounter, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WaitingCounter> findWithLockByRestaurantId(Long restaurantId);

    Optional<WaitingCounter> findByRestaurantId(Long restaurantId);

    // 식당 첫 등록의 동시 생성 경합 대비 — 이미 있으면 조용히 무시 (PostgreSQL upsert)
    @Modifying
    @Query(value = "insert into waiting_counter (restaurant_id, last_no) values (:restaurantId, 0) "
            + "on conflict (restaurant_id) do nothing", nativeQuery = true)
    void insertIfAbsent(@Param("restaurantId") Long restaurantId);
}
