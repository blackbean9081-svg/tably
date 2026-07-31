package com.app.tably.waiting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 2막(tably.waiting.rank-mode=redis): 식당별 Sorted Set(score=waitingNo, member=waitingId).
 *
 * - aheadCount = 내 번호보다 작은 score의 개수(ZCOUNT) — O(log N), DB에 닿지 않는다.
 * - 내 waitingId가 집합에 없으면(ZSCORE null) Redis 재기동 등으로 유실된 것 —
 *   0팀으로 잘못 답하는 대신 empty로 답해 DB 폴백을 태운다.
 * - 갱신(add/remove) 실패는 로그만 남긴다: 인덱스는 읽기 모델일 뿐, 실패가 등록·호출을 막으면 안 된다.
 */
@Component
@ConditionalOnProperty(name = "tably.waiting.rank-mode", havingValue = "redis")
@RequiredArgsConstructor
@Slf4j
public class RedisWaitingRankIndex implements WaitingRankIndex {

    static final String KEY_PREFIX = "waiting:queue:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void add(Long restaurantId, Long waitingId, int waitingNo) {
        try {
            redisTemplate.opsForZSet().add(key(restaurantId), String.valueOf(waitingId), waitingNo);
        } catch (DataAccessException e) {
            log.warn("웨이팅 인덱스 추가 실패 — 폴링은 DB 폴백으로 동작 (waiting {}): {}", waitingId, e.getMessage());
        }
    }

    @Override
    public void remove(Long restaurantId, Long waitingId) {
        try {
            redisTemplate.opsForZSet().remove(key(restaurantId), String.valueOf(waitingId));
        } catch (DataAccessException e) {
            // 남은 유령 멤버는 앞 팀 수를 부풀린다 — 조회 폴백 조건(ZSCORE 확인)으로는 안 걸러지므로 기록해 둔다
            log.warn("웨이팅 인덱스 제거 실패 (waiting {}): {}", waitingId, e.getMessage());
        }
    }

    @Override
    public Optional<Long> aheadCount(Long restaurantId, Long waitingId, int waitingNo) {
        try {
            Double myScore = redisTemplate.opsForZSet().score(key(restaurantId), String.valueOf(waitingId));
            if (myScore == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(
                    redisTemplate.opsForZSet().count(key(restaurantId), 0, waitingNo - 1));
        } catch (DataAccessException e) {
            log.warn("웨이팅 인덱스 조회 실패 — DB 폴백 (waiting {}): {}", waitingId, e.getMessage());
            return Optional.empty();
        }
    }

    private String key(Long restaurantId) {
        return KEY_PREFIX + restaurantId;
    }
}
