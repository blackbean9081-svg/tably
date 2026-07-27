package com.app.tably.reservation.repository;

import com.app.tably.reservation.entity.AppealStatus;
import com.app.tably.reservation.entity.NoShowAppeal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoShowAppealRepository extends JpaRepository<NoShowAppeal, Long> {

    boolean existsByReservationIdAndStatus(Long reservationId, AppealStatus status);

    // 운영자 심사 대기열
    List<NoShowAppeal> findAllByStatusOrderByIdAsc(AppealStatus status);
}
