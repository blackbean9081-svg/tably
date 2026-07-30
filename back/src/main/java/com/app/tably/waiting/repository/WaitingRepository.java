package com.app.tably.waiting.repository;

import com.app.tably.waiting.entity.Waiting;
import com.app.tably.waiting.entity.WaitingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WaitingRepository extends JpaRepository<Waiting, Long> {

    boolean existsByRestaurantIdAndMemberIdAndStatusIn(Long restaurantId, Long memberId,
                                                       Collection<WaitingStatus> statuses);

    // "내 앞에 N팀" — 순번 조회는 매우 잦으므로 count 쿼리 하나로 (S6)
    long countByRestaurantIdAndStatusAndWaitingNoLessThan(Long restaurantId, WaitingStatus status, int waitingNo);

    Optional<Waiting> findFirstByRestaurantIdAndStatusOrderByWaitingNoAsc(Long restaurantId, WaitingStatus status);

    // 대기번호 발급(핵심영역 6)의 재료 — 동시 등록 경합은 발급 로직이 해결할 것
    Optional<Waiting> findTopByRestaurantIdOrderByWaitingNoDesc(Long restaurantId);

    @Modifying(clearAutomatically = true)
    @Query("update Waiting w set w.status = :target where w.id = :id and w.status in :currents")
    int updateStatusIfCurrentIn(@Param("id") Long id,
                                @Param("currents") Collection<WaitingStatus> currents,
                                @Param("target") WaitingStatus target);

    @Modifying(clearAutomatically = true)
    @Query("update Waiting w set w.status = :target where w.status = :current and w.calledAt < :threshold")
    int updateStatusAllCalledBefore(@Param("current") WaitingStatus current,
                                    @Param("threshold") LocalDateTime threshold,
                                    @Param("target") WaitingStatus target);

    // 동시성 테스트의 검증·정리용
    List<Waiting> findAllByRestaurantId(Long restaurantId);
}
