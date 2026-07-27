package com.app.tably.settlement.repository;

import com.app.tably.reservation.entity.Reservation;
import com.app.tably.reservation.entity.ReservationStatus;
import com.app.tably.settlement.entity.SettlementItem;
import com.app.tably.settlement.entity.SettlementItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {

    List<SettlementItem> findAllBySettlementIdOrderByIdAsc(Long settlementId);

    Optional<SettlementItem> findByReservationIdAndType(Long reservationId, SettlementItemType type);

    // FORFEIT 후보: 해당 월의 노쇼 중 아직 정산에 안 잡힌 건 — "이미 처리된 건 제외"가 재실행 안전의 핵심 (FR-17)
    @Query("""
            select r from Reservation r
            where r.slot.restaurant.id = :restaurantId
              and r.status = :status
              and r.slot.slotDate between :start and :end
              and not exists (
                  select 1 from SettlementItem i
                  where i.reservation = r and i.type = :type)
            """)
    List<Reservation> findForfeitCandidates(@Param("restaurantId") Long restaurantId,
                                            @Param("start") LocalDate start,
                                            @Param("end") LocalDate end,
                                            @Param("status") ReservationStatus status,
                                            @Param("type") SettlementItemType type);

    // 차감 후보: 몰수(FORFEIT)로 지급까지 됐다가 철회된 건 중 아직 차감 안 된 건 (FR-19)
    @Query("""
            select r from Reservation r
            where r.slot.restaurant.id = :restaurantId
              and r.status = :status
              and exists (
                  select 1 from SettlementItem f
                  where f.reservation = r and f.type = :forfeitType)
              and not exists (
                  select 1 from SettlementItem a
                  where a.reservation = r and a.type = :adjustmentType)
            """)
    List<Reservation> findRevokeCandidates(@Param("restaurantId") Long restaurantId,
                                           @Param("status") ReservationStatus status,
                                           @Param("forfeitType") SettlementItemType forfeitType,
                                           @Param("adjustmentType") SettlementItemType adjustmentType);
}
