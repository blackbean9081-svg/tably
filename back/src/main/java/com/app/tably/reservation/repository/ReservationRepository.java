package com.app.tably.reservation.repository;

import com.app.tably.reservation.entity.Reservation;
import com.app.tably.reservation.entity.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // 결제 승인·취소가 같은 예약을 두고 경합하지 않도록 행 단위로 직렬화 (핵심영역 4)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reservation> findWithLockById(Long id);

    // 조건부 UPDATE — 현재 상태가 기대와 다르면 0건. 배치 vs 사장 처리 같은 경합에서 한쪽만 이긴다
    @Modifying(clearAutomatically = true)
    @Query("update Reservation r set r.status = :target where r.id = :id and r.status = :current")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("current") ReservationStatus current,
                              @Param("target") ReservationStatus target);

    // 선점 만료 일괄 전이 — 결제 완료로 상태가 바뀐 행은 조건에서 자연히 빠진다 (FR-05)
    @Modifying(clearAutomatically = true)
    @Query("update Reservation r set r.status = :target where r.status = :current and r.heldAt < :threshold")
    int updateStatusAllHeldBefore(@Param("current") ReservationStatus current,
                                  @Param("threshold") LocalDateTime threshold,
                                  @Param("target") ReservationStatus target);

    // 휴업 일괄 취소(S8) 대상: 기간 내 확정 예약
    @Query("""
            select r.id from Reservation r
            where r.slot.restaurant.id = :restaurantId
              and r.slot.slotDate between :start and :end
              and r.status = :status
            """)
    List<Long> findIdsByRestaurantAndSlotDateBetweenAndStatus(@Param("restaurantId") Long restaurantId,
                                                              @Param("start") LocalDate start,
                                                              @Param("end") LocalDate end,
                                                              @Param("status") ReservationStatus status);

    // 노쇼 배치 대상: 슬롯 시각이 threshold(now - 30분) 이전인 예약 (FR-12)
    @Query("""
            select r.id from Reservation r
            where r.status = :status
              and (r.slot.slotDate < :date or (r.slot.slotDate = :date and r.slot.slotTime < :time))
            """)
    List<Long> findIdsByStatusAndSlotBefore(@Param("status") ReservationStatus status,
                                            @Param("date") LocalDate date,
                                            @Param("time") LocalTime time);

    List<Reservation> findAllByMemberIdOrderByIdDesc(Long memberId);

    // 슬롯의 활성 예약(PENDING_PAYMENT/CONFIRMED) 존재 확인 — 선점 로직(핵심영역 1)의 재료
    Optional<Reservation> findBySlotIdAndStatusIn(Long slotId, Collection<ReservationStatus> statuses);

    boolean existsBySlotIdAndStatusIn(Long slotId, Collection<ReservationStatus> statuses);

    // 선점 10분 만료 배치의 조회 대상
    List<Reservation> findAllByStatusAndHeldAtBefore(ReservationStatus status, LocalDateTime threshold);

    // 동시성 테스트의 "활성 예약 정확히 1건" 검증용
    long countBySlotIdAndStatusIn(Long slotId, Collection<ReservationStatus> statuses);

    List<Reservation> findAllBySlotId(Long slotId);
}
