package com.app.tably.waiting.service;

import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.RestaurantRepository;
import com.app.tably.waiting.dto.WaitingRegisterRequestDto;
import com.app.tably.waiting.dto.WaitingResponseDto;
import com.app.tably.waiting.repository.WaitingCounterRepository;
import com.app.tably.waiting.repository.WaitingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 2막(rank-mode=redis) 웨이팅 순번 조회 검증 — 등록·호출이 Sorted Set에 반영되고,
 * 폴링이 DB count 대신 인덱스로 답하는지, 인덱스 유실 시 DB로 폴백하는지를 본다.
 * 로컬 PostgreSQL + Redis 필요. Redis가 없으면 스킵 (CI는 redis 서비스 컨테이너로 실행).
 */
@SpringBootTest(properties = "tably.waiting.rank-mode=redis")
class RedisWaitingRankIntegrationTest {

    @Autowired
    private WaitingService waitingService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RestaurantRepository restaurantRepository;
    @Autowired
    private WaitingRepository waitingRepository;
    @Autowired
    private WaitingCounterRepository waitingCounterRepository;

    private Member owner;
    private Restaurant restaurant;
    private List<Member> guests;

    @BeforeEach
    void setUp() {
        assumeTrue(redisAvailable(), "Redis 미기동 — 인덱스 경로 검증 불가로 스킵");

        String run = Long.toString(System.nanoTime());
        owner = memberRepository.save(Member.builder()
                .name("웨이팅레디스사장").password("pw").email("wrank-owner-" + run + "@test.local").role(Role.OWNER).build());
        restaurant = restaurantRepository.save(Restaurant.builder()
                .owner(owner).name("웨이팅레디스식당-" + run).build());
        guests = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            guests.add(memberRepository.save(Member.builder()
                    .name("게스트" + i).password("pw").email("wrank-guest-" + i + "-" + run + "@test.local").role(Role.GUEST).build()));
        }
    }

    @AfterEach
    void tearDown() {
        if (restaurant == null) {
            return;     // Redis 미기동으로 스킵된 경우
        }
        redisTemplate.delete(RedisWaitingRankIndex.KEY_PREFIX + restaurant.getId());
        waitingRepository.deleteAll(waitingRepository.findAllByRestaurantId(restaurant.getId()));
        waitingCounterRepository.findByRestaurantId(restaurant.getId())
                .ifPresent(waitingCounterRepository::delete);
        restaurantRepository.delete(restaurant);
        memberRepository.deleteAll(guests);
        memberRepository.delete(owner);
    }

    @Test
    @DisplayName("등록 3팀 → 인덱스가 앞 팀 수를 답하고, 호출되면 뒤 팀의 앞 팀 수가 줄어든다")
    void rankIndex_reflectsRegisterAndCall() {
        WaitingResponseDto first = waitingService.register(guests.get(0).getId(),
                new WaitingRegisterRequestDto(restaurant.getId()));
        WaitingResponseDto second = waitingService.register(guests.get(1).getId(),
                new WaitingRegisterRequestDto(restaurant.getId()));
        WaitingResponseDto third = waitingService.register(guests.get(2).getId(),
                new WaitingRegisterRequestDto(restaurant.getId()));

        assertThat(redisTemplate.opsForZSet().zCard(RedisWaitingRankIndex.KEY_PREFIX + restaurant.getId()))
                .as("등록 3건이 모두 Sorted Set에 반영").isEqualTo(3);
        assertThat(waitingService.getMyWaiting(guests.get(2).getId(), third.id()).aheadCount())
                .as("3번째 팀 앞에는 2팀").isEqualTo(2);

        waitingService.callNext(owner.getId(), restaurant.getId());

        assertThat(waitingService.getMyWaiting(guests.get(2).getId(), third.id()).aheadCount())
                .as("1팀이 호출돼 대기 집합에서 빠지면 앞 팀 수도 준다").isEqualTo(1);
        assertThat(waitingService.getMyWaiting(guests.get(0).getId(), first.id()).status().name())
                .isEqualTo("CALLED");
        assertThat(second.aheadCount()).as("등록 응답의 앞 팀 수(DB 계산)도 일치").isEqualTo(1);
    }

    @Test
    @DisplayName("인덱스 유실(키 삭제) 시 폴링은 DB count로 폴백 — 0팀으로 잘못 답하지 않는다")
    void rankIndex_lost_fallsBackToDb() {
        waitingService.register(guests.get(0).getId(), new WaitingRegisterRequestDto(restaurant.getId()));
        WaitingResponseDto second = waitingService.register(guests.get(1).getId(),
                new WaitingRegisterRequestDto(restaurant.getId()));

        redisTemplate.delete(RedisWaitingRankIndex.KEY_PREFIX + restaurant.getId());

        assertThat(waitingService.getMyWaiting(guests.get(1).getId(), second.id()).aheadCount())
                .as("인덱스가 비어도 DB가 진실 — 앞 팀 1팀").isEqualTo(1);
    }

    private boolean redisAvailable() {
        try {
            return "PONG".equals(redisTemplate.execute(connection -> connection.ping(), true));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
