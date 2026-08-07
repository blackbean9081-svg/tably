package com.app.tably.waiting.service;

import java.util.Optional;

/**
 * 2막: 순번 조회(FR-15) 폴링용 읽기 모델.
 *
 * "내 앞에 N팀"은 모두가 계속 새로고침하는 최다 빈도 조회다 — 1막은 매 폴링이 DB count 쿼리.
 * 이 인덱스는 그 조회를 DB 밖(Redis Sorted Set)으로 빼는 역할만 하며,
 * 대기의 진실 원천(등록·상태·번호)은 언제나 DB다. 인덱스가 유실되면 조회는
 * Optional.empty()로 답하고 호출부가 DB count로 폴백한다 — 틀린 숫자를 보여주느니 느린 숫자를 보여준다.
 */
public interface WaitingRankIndex {

    /**
     * 등록 커밋 후 호출 — 대기열 인덱스에 (waitingId, waitingNo) 추가.
     */
    void add(Long restaurantId, Long waitingId, int waitingNo);

    /**
     * 호출·취소로 대기(WAITING) 집합에서 빠질 때 호출.
     */
    void remove(Long restaurantId, Long waitingId);

    /**
     * 내 앞의 대기 팀 수. 인덱스가 답할 수 없으면(미사용 모드·유실·장애) empty — 호출부가 DB로 폴백.
     */
    Optional<Long> aheadCount(Long restaurantId, Long waitingId, int waitingNo);
}
