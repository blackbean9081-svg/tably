package com.app.tably.waiting.service;

import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.RestaurantRepository;
import com.app.tably.waiting.dto.WaitingRegisterRequestDto;
import com.app.tably.waiting.dto.WaitingResponseDto;
import com.app.tably.waiting.entity.Waiting;
import com.app.tably.waiting.repository.WaitingCounterRepository;
import com.app.tably.waiting.repository.WaitingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 핵심영역 6(대기번호 발급) 동시성 검증.
 * 카운터 행 락은 실제 DB가 걸기 때문에 mock으로는 검증 불가 — 로컬 PostgreSQL 필요.
 * 스레드마다 트랜잭션이 따로 돌아야 하므로 테스트에 @Transactional을 붙이지 않는다 (정리는 tearDown에서 수동).
 */
@SpringBootTest
class WaitingRegisterConcurrencyTest {

    private static final int THREAD_COUNT = 20;

    @Autowired
    private WaitingService waitingService;
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
        // 개발 DB를 공유하므로 시드 데이터와 겹치지 않는 고유 식별자 사용
        String run = Long.toString(System.nanoTime());
        owner = memberRepository.save(Member.builder()
                .name("웨이팅테스트사장").password("pw").email("wait-owner-" + run + "@test.local").role(Role.OWNER).build());
        restaurant = restaurantRepository.save(Restaurant.builder()
                .owner(owner).name("웨이팅테스트식당-" + run).build());
        guests = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            guests.add(memberRepository.save(Member.builder()
                    .name("게스트" + i).password("pw").email("wait-guest-" + i + "-" + run + "@test.local").role(Role.GUEST).build()));
        }
    }

    @AfterEach
    void tearDown() {
        waitingRepository.deleteAll(waitingRepository.findAllByRestaurantId(restaurant.getId()));
        waitingCounterRepository.findByRestaurantId(restaurant.getId())
                .ifPresent(waitingCounterRepository::delete);
        restaurantRepository.delete(restaurant);
        memberRepository.deleteAll(guests);
        memberRepository.delete(owner);
    }

    @Test
    @DisplayName("동시 20명 등록 → 전원 성공, 대기번호는 1~20 중복·누락 없음 (S6)")
    void register_concurrent20_noDuplicateNumbers() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(THREAD_COUNT);
        List<WaitingResponseDto> results = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        for (Member guest : guests) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    results.add(waitingService.register(guest.getId(), new WaitingRegisterRequestDto(restaurant.getId())));
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("30초 내 전원 완료").isTrue();
        assertThat(failures).as("등록 실패는 없어야 함").isEmpty();
        assertThat(results).hasSize(THREAD_COUNT);
        assertThat(results.stream().map(WaitingResponseDto::waitingNo))
                .as("번호는 1~%d, 중복·누락 없음".formatted(THREAD_COUNT))
                .containsExactlyInAnyOrderElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, THREAD_COUNT).boxed().toList());

        List<Waiting> saved = waitingRepository.findAllByRestaurantId(restaurant.getId());
        assertThat(saved.stream().map(Waiting::getWaitingNo).distinct()).as("DB에도 중복 없음").hasSize(THREAD_COUNT);
    }
}
