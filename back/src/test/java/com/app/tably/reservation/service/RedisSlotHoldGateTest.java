package com.app.tably.reservation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RedisSlotHoldGateTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private RedisSlotHoldGate gate;

    @Test
    @DisplayName("SET NX 승자만 true — 키는 슬롯별, TTL은 결제 시한(10분)")
    void tryAcquire_winner() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent("slot:hold:20", "1", ReservationService.HOLD_TIMEOUT))
                .willReturn(true);

        assertThat(gate.tryAcquire(20L, 1L)).isTrue();
    }

    @Test
    @DisplayName("이미 키가 있으면 false — DB에 닿지 않고 즉시 실패시킬 근거")
    void tryAcquire_loser() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent("slot:hold:20", "2", ReservationService.HOLD_TIMEOUT))
                .willReturn(false);

        assertThat(gate.tryAcquire(20L, 2L)).isFalse();
    }

    @Test
    @DisplayName("Redis 장애는 fail-open — 게이트를 통과시켜 DB 락 경로로 동작 (정합성은 DB 몫)")
    void tryAcquire_failOpenOnRedisDown() {
        given(redisTemplate.opsForValue()).willThrow(new RedisConnectionFailureException("down"));

        assertThat(gate.tryAcquire(20L, 1L)).isTrue();
    }

    @Test
    @DisplayName("release는 슬롯 키를 삭제하고, 실패해도 예외를 전파하지 않는다 (TTL이 수습)")
    void release_deletesKeyAndSwallowsFailure() {
        gate.release(20L);
        then(redisTemplate).should().delete("slot:hold:20");

        given(redisTemplate.delete("slot:hold:21"))
                .willThrow(new RedisConnectionFailureException("down"));
        assertThatCode(() -> gate.release(21L)).doesNotThrowAnyException();
    }
}
