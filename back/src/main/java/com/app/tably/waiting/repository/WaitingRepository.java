package com.app.tably.waiting.repository;

import com.app.tably.waiting.entity.Waiting;
import com.app.tably.waiting.entity.WaitingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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

    // 호출 10분 만료 배치의 조회 대상
    List<Waiting> findAllByStatusAndCalledAtBefore(WaitingStatus status, LocalDateTime threshold);

    // 동시성 테스트의 검증·정리용
    List<Waiting> findAllByRestaurantId(Long restaurantId);
}
