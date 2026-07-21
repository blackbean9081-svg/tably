package com.app.tably.slot.repository;

import com.app.tably.slot.entity.Slot;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    List<Slot> findAllByRestaurantIdAndSlotDateOrderBySlotTimeAscTableNoAsc(Long restaurantId, LocalDate slotDate);

    boolean existsByRestaurantIdAndSlotDateBetween(Long restaurantId, LocalDate start, LocalDate end);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name ="jakarta.persistence.lock.timeout" , value ="0"))
    Optional<Slot> findWithLockById(Long id);


}
