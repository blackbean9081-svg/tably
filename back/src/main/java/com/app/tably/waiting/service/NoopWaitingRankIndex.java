package com.app.tably.waiting.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 1막(tably.waiting.rank-mode=db, 기본값): 인덱스 없음 — 순번 조회는 항상 DB count.
 */
@Component
@ConditionalOnProperty(name = "tably.waiting.rank-mode", havingValue = "db", matchIfMissing = true)
public class NoopWaitingRankIndex implements WaitingRankIndex {

    @Override
    public void add(Long restaurantId, Long waitingId, int waitingNo) {
    }

    @Override
    public void remove(Long restaurantId, Long waitingId) {
    }

    @Override
    public Optional<Long> aheadCount(Long restaurantId, Long waitingId, int waitingNo) {
        return Optional.empty();
    }
}
