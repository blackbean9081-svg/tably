package com.app.tably.reservation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 2막(tably.slot-hold.mode=redis): SET NX + TTL 기반 분산락 게이트.
 *
 * - 같은 슬롯의 동시 요청 중 SET NX 승자 1명만 DB로 내려가고, 나머지는 Redis 왕복 한 번으로
 *   즉시 409를 받는다 — "실패 응답도 빨라야 한다"(S2)를 락 대기 없이 만족시킨다.
 * - TTL = 선점 결제 시한(10분): 결제로 CONFIRMED가 되든 미결제로 EXPIRED가 되든
 *   키는 시한과 함께 소멸하므로 별도 정리 배치가 필요 없다. TTL 소멸 후 통과한 요청은
 *   DB의 활성 예약 검증이 걸러낸다 (CONFIRMED 슬롯 재선점 차단).
 * - Redis 장애 시 fail-open: 게이트를 통과시켜 1막 경로(DB 락)로 동작한다.
 *   가용성을 지키는 대신 스파이크 완충만 잃는 선택 — 정합성은 DB가 지키므로 가능한 트레이드오프.
 */
@Component
@ConditionalOnProperty(name = "tably.slot-hold.mode", havingValue = "redis")
@RequiredArgsConstructor
@Slf4j
public class RedisSlotHoldGate implements SlotHoldGate {

    static final String KEY_PREFIX = "slot:hold:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryAcquire(Long slotId, Long memberId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                    KEY_PREFIX + slotId, String.valueOf(memberId), ReservationService.HOLD_TIMEOUT));
        } catch (DataAccessException e) {
            log.warn("Redis 선점 게이트 장애 — DB 락 경로로 fail-open (slot {}): {}", slotId, e.getMessage());
            return true;
        }
    }

    @Override
    public void release(Long slotId) {
        try {
            redisTemplate.delete(KEY_PREFIX + slotId);
        } catch (DataAccessException e) {
            // 해제 실패는 TTL이 수습한다 — 최대 10분간 해당 슬롯 재선점이 게이트에서 막힐 뿐
            log.warn("Redis 선점 게이트 해제 실패 — TTL 소멸 대기 (slot {}): {}", slotId, e.getMessage());
        }
    }
}
