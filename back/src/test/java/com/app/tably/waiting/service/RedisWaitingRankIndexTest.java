package com.app.tably.waiting.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RedisWaitingRankIndexTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    @InjectMocks
    private RedisWaitingRankIndex index;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOps);
    }

    @Test
    @DisplayName("aheadCount — 내 번호보다 작은 score의 개수(ZCOUNT)로 답한다")
    void aheadCount_countsSmallerScores() {
        given(zSetOps.score("waiting:queue:10", "5")).willReturn(12.0);
        given(zSetOps.count("waiting:queue:10", 0, 11)).willReturn(7L);

        assertThat(index.aheadCount(10L, 5L, 12)).contains(7L);
    }

    @Test
    @DisplayName("내 waitingId가 집합에 없으면(유실) empty — 0팀으로 잘못 답하지 않고 DB 폴백을 태운다")
    void aheadCount_emptyWhenMemberMissing() {
        given(zSetOps.score("waiting:queue:10", "5")).willReturn(null);

        assertThat(index.aheadCount(10L, 5L, 12)).isEmpty();
    }

    @Test
    @DisplayName("Redis 장애 시 empty — 조회는 DB 폴백, 갱신 실패는 전파하지 않는다")
    void failuresDegradeGracefully() {
        given(zSetOps.score("waiting:queue:10", "5"))
                .willThrow(new RedisConnectionFailureException("down"));
        assertThat(index.aheadCount(10L, 5L, 12)).isEmpty();

        given(zSetOps.add("waiting:queue:10", "5", 12))
                .willThrow(new RedisConnectionFailureException("down"));
        assertThatCode(() -> index.add(10L, 5L, 12)).doesNotThrowAnyException();

        given(zSetOps.remove("waiting:queue:10", "5"))
                .willThrow(new RedisConnectionFailureException("down"));
        assertThatCode(() -> index.remove(10L, 5L)).doesNotThrowAnyException();
    }
}
