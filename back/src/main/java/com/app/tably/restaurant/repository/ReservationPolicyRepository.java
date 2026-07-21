package com.app.tably.restaurant.repository;

import com.app.tably.restaurant.entity.ReservationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservationPolicyRepository extends JpaRepository<ReservationPolicy, Long> {

    Optional<ReservationPolicy> findByRestaurantId(Long restaurantId);
}
