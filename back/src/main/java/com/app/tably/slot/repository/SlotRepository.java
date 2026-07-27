package com.app.tably.slot.repository;

import com.app.tably.slot.entity.Slot;
import com.app.tably.slot.entity.SlotStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    List<Slot> findAllByRestaurantIdAndSlotDateOrderBySlotTimeAscTableNoAsc(Long restaurantId, LocalDate slotDate);

    boolean existsByRestaurantIdAndSlotDateBetween(Long restaurantId, LocalDate start, LocalDate end);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name ="jakarta.persistence.lock.timeout" , value ="0"))
    Optional<Slot> findWithLockById(Long id);

    // 휴업 기간 슬롯 일괄 닫기 (S8) — 새 선점을 막는 게 우선이라 취소 처리보다 먼저 실행된다
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update Slot s set s.status = :target
            where s.restaurant.id = :restaurantId
              and s.slotDate between :start and :end
              and s.status = :current
            """)
    int updateStatusAllBetween(@Param("restaurantId") Long restaurantId,
                               @Param("start") LocalDate start,
                               @Param("end") LocalDate end,
                               @Param("current") SlotStatus current,
                               @Param("target") SlotStatus target);
}
