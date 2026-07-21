package com.app.tably.reservation.repository;

import com.app.tably.reservation.entity.Reservation;
import com.app.tably.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findAllByMemberIdOrderByIdDesc(Long memberId);

    // 슬롯의 활성 예약(PENDING_PAYMENT/CONFIRMED) 존재 확인 — 선점 로직(핵심영역 1)의 재료
    Optional<Reservation> findBySlotIdAndStatusIn(Long slotId, Collection<ReservationStatus> statuses);

    boolean existsBySlotIdAndStatusIn(Long slotId, Collection<ReservationStatus> statuses);

    // 선점 10분 만료 배치의 조회 대상
    List<Reservation> findAllByStatusAndHeldAtBefore(ReservationStatus status, LocalDateTime threshold);
}
