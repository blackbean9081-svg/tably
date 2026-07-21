package com.app.tably.slot.repository;

import com.app.tably.slot.entity.Slot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    List<Slot> findAllByRestaurantIdAndSlotDateOrderBySlotTimeAscTableNoAsc(Long restaurantId, LocalDate slotDate);

    boolean existsByRestaurantIdAndSlotDateBetween(Long restaurantId, LocalDate start, LocalDate end);
}
