package com.app.tably.reservation.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.reservation.dto.ReservationHoldRequestDto;
import com.app.tably.reservation.entity.ReservationStatus;
import com.app.tably.reservation.repository.ReservationRepository;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.RestaurantRepository;
import com.app.tably.slot.entity.Slot;
import com.app.tably.slot.entity.SlotStatus;
import com.app.tably.slot.repository.SlotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 2막(mode=redis) 슬롯 선점 동시성 검증 — DB 모드 테스트(ReservationHoldConcurrencyTest)와
 * 같은 시나리오를 Redis 게이트 경로로 돌려, 전환 후에도 "성공 1명·중복 0건"이 유지됨을 본다.
 * 로컬 PostgreSQL + Redis 필요. Redis가 없으면 스킵된다 (CI는 redis 서비스 컨테이너로 실행).
 */
@SpringBootTest(properties = "tably.slot-hold.mode=redis")
class RedisSlotHoldConcurrencyTest {

    private static final int THREAD_COUNT = 20;
    private static final List<ReservationStatus> ACTIVE =
            List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED);

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RestaurantRepository restaurantRepository;
    @Autowired
    private SlotRepository slotRepository;
    @Autowired
    private ReservationRepository reservationRepository;

    private Member owner;
    private Restaurant restaurant;
    private Slot slot;
    private List<Member> guests;

    @BeforeEach
    void setUp() {
        assumeTrue(redisAvailable(), "Redis 미기동 — 게이트 경로 검증 불가로 스킵 (fail-open이라 통과는 하지만 무의미)");

        String run = Long.toString(System.nanoTime());
        owner = memberRepository.save(Member.builder()
                .name("레디스테스트사장").password("pw").email("redis-owner-" + run + "@test.local").role(Role.OWNER).build());
        restaurant = restaurantRepository.save(Restaurant.builder()
                .owner(owner).name("레디스테스트식당-" + run).build());
        slot = slotRepository.save(Slot.builder()
                .restaurant(restaurant).slotDate(LocalDate.of(2099, 2, 1)).slotTime(LocalTime.of(18, 0))
                .tableNo(1).status(SlotStatus.OPEN).build());
        guests = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            guests.add(memberRepository.save(Member.builder()
                    .name("게스트" + i).password("pw").email("redis-guest-" + i + "-" + run + "@test.local").role(Role.GUEST).build()));
        }
    }

    @AfterEach
    void tearDown() {
        if (slot == null) {
            return;     // Redis 미기동으로 스킵된 경우
        }
        redisTemplate.delete(RedisSlotHoldGate.KEY_PREFIX + slot.getId());
        reservationRepository.deleteAll(reservationRepository.findAllBySlotId(slot.getId()));
        slotRepository.delete(slot);
        restaurantRepository.delete(restaurant);
        memberRepository.deleteAll(guests);
        memberRepository.delete(owner);
    }

    @Test
    @DisplayName("Redis 게이트 — 동시 20명 선점 시 성공 1명, 나머지 409, DB 활성 예약 1건, 게이트 키 잔존")
    void hold_concurrent20_gateAdmitsExactlyOne() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(THREAD_COUNT);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger taken = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        for (Member guest : guests) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    reservationService.hold(guest.getId(), new ReservationHoldRequestDto(slot.getId(), 2));
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.SLOT_ALREADY_TAKEN) {
                        taken.incrementAndGet();
                    } else {
                        unexpected.add(e);
                    }
                } catch (Throwable t) {
                    unexpected.add(t);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(unexpected).as("SLOT_ALREADY_TAKEN 외 예외는 없어야 함").isEmpty();
        assertThat(success.get()).as("성공은 정확히 1명").isEqualTo(1);
        assertThat(taken.get()).as("나머지는 전원 409").isEqualTo(THREAD_COUNT - 1);
        assertThat(reservationRepository.countBySlotIdAndStatusIn(slot.getId(), ACTIVE))
                .as("전환 후에도 중복 예약 0건 (S2)").isEqualTo(1);
        assertThat(redisTemplate.hasKey(RedisSlotHoldGate.KEY_PREFIX + slot.getId()))
                .as("승자의 게이트 키가 TTL과 함께 남아 후속 요청을 DB 앞에서 차단").isTrue();
    }

    @Test
    @DisplayName("선점 실패(활성 예약 존재) 시 게이트가 되돌려져 다음 손님이 게이트에서 막히지 않는다")
    void hold_gateReleasedWhenDbRejects() {
        Long winnerId = reservationService.hold(guests.get(0).getId(), new ReservationHoldRequestDto(slot.getId(), 2));
        assertThat(winnerId).isNotNull();

        // 게이트 키를 지워 게이트는 통과시키되 DB 활성 예약 검증에서 거절되는 상황을 만든다
        redisTemplate.delete(RedisSlotHoldGate.KEY_PREFIX + slot.getId());

        AtomicInteger taken = new AtomicInteger();
        try {
            reservationService.hold(guests.get(1).getId(), new ReservationHoldRequestDto(slot.getId(), 2));
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.SLOT_ALREADY_TAKEN) {
                taken.incrementAndGet();
            }
        }

        assertThat(taken.get()).as("DB 검증이 거절 — 게이트 유실에도 중복은 생기지 않는다").isEqualTo(1);
        assertThat(redisTemplate.hasKey(RedisSlotHoldGate.KEY_PREFIX + slot.getId()))
                .as("실패한 요청이 잡았던 게이트 키는 되돌려진다").isFalse();
    }

    private boolean redisAvailable() {
        try {
            return "PONG".equals(redisTemplate.execute(
                    connection -> connection.ping(), true));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
