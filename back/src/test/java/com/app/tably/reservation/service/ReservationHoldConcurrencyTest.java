package com.app.tably.reservation.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.reservation.dto.ReservationHoldRequestDto;
import com.app.tably.reservation.entity.Reservation;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 핵심영역 1(슬롯 선점) 동시성 검증.
 * 행 락(FOR UPDATE NOWAIT)은 실제 DB가 걸기 때문에 mock으로는 검증 불가 — 로컬 PostgreSQL 필요.
 * 스레드마다 트랜잭션이 따로 돌아야 하므로 테스트에 @Transactional을 붙이지 않는다 (정리는 tearDown에서 수동).
 */
@SpringBootTest
class ReservationHoldConcurrencyTest {

    private static final int THREAD_COUNT = 20;
    private static final List<ReservationStatus> ACTIVE =
            List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED);

    @Autowired
    private ReservationService reservationService;
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
        // 개발 DB를 공유하므로 시드 데이터와 겹치지 않는 고유 식별자 사용 (이전 실행이 중단됐어도 충돌 없음)
        String run = Long.toString(System.nanoTime());
        owner = memberRepository.save(Member.builder()
                .name("동시성테스트사장").password("pw").email("conc-owner-" + run + "@test.local").role(Role.OWNER).build());
        restaurant = restaurantRepository.save(Restaurant.builder()
                .owner(owner).name("동시성테스트식당-" + run).build());
        slot = slotRepository.save(Slot.builder()
                .restaurant(restaurant).slotDate(LocalDate.of(2099, 1, 1)).slotTime(LocalTime.of(18, 0))
                .tableNo(1).status(SlotStatus.OPEN).build());
        guests = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            guests.add(memberRepository.save(Member.builder()
                    .name("게스트" + i).password("pw").email("conc-guest-" + i + "-" + run + "@test.local").role(Role.GUEST).build()));
        }
    }

    @AfterEach
    void tearDown() {
        // FK 역순으로 이 테스트가 만든 행만 삭제
        reservationRepository.deleteAll(reservationRepository.findAllBySlotId(slot.getId()));
        slotRepository.delete(slot);
        restaurantRepository.delete(restaurant);
        memberRepository.deleteAll(guests);
        memberRepository.delete(owner);
    }

    @Test
    @DisplayName("같은 슬롯에 동시 20명 선점 → 성공 1명, 나머지 전원 SLOT_ALREADY_TAKEN, DB 활성 예약 1건")
    void hold_concurrent20_exactlyOneWins() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startGate = new CountDownLatch(1);      // 전원 준비 후 동시 출발 — 오픈런 재현
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

        assertThat(finished).as("30초 내 전원 완료 — 락 대기로 스레드가 묶이면 실패").isTrue();
        assertThat(unexpected).as("SLOT_ALREADY_TAKEN 외 예외는 없어야 함").isEmpty();
        assertThat(success.get()).as("성공은 정확히 1명").isEqualTo(1);
        assertThat(taken.get()).as("나머지는 전원 409").isEqualTo(THREAD_COUNT - 1);
        assertThat(reservationRepository.countBySlotIdAndStatusIn(slot.getId(), ACTIVE))
                .as("DB의 활성 예약도 정확히 1건 — 중복 예약 0건 (S2)").isEqualTo(1);
    }

    @Test
    @DisplayName("EXPIRED 예약만 있던 슬롯은 다시 선점할 수 있다 (조건 6)")
    void hold_reHoldableAfterExpired() {
        reservationRepository.save(Reservation.builder()
                .slot(slot).member(guests.get(0)).partySize(2)
                .status(ReservationStatus.EXPIRED).heldAt(LocalDateTime.now().minusMinutes(20)).build());

        Long newId = reservationService.hold(guests.get(1).getId(), new ReservationHoldRequestDto(slot.getId(), 2));

        assertThat(newId).isNotNull();
        assertThat(reservationRepository.countBySlotIdAndStatusIn(slot.getId(), ACTIVE)).isEqualTo(1);
    }

    @Test
    @DisplayName("CLOSED 슬롯 선점은 SLOT_CLOSED (조건 3)")
    void hold_closedSlot() {
        slot.close();
        slotRepository.save(slot);

        assertThatThrownBy(() -> reservationService.hold(guests.get(0).getId(), new ReservationHoldRequestDto(slot.getId(), 2)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SLOT_CLOSED);
    }
}
