package com.app.tably.config;

import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.restaurant.entity.ReservationPolicy;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.ReservationPolicyRepository;
import com.app.tably.restaurant.repository.RestaurantRepository;
import com.app.tably.slot.dto.SlotGenerateRequestDto;
import com.app.tably.slot.service.SlotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

/**
 * 로컬 시연용 시드 데이터. member 테이블이 비어 있을 때 1회만 실행된다.
 * 계정: guest@tably.com / owner@tably.com (비밀번호 모두 password123!)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final String SEED_PASSWORD = "password123!";

    private final MemberRepository memberRepository;
    private final RestaurantRepository restaurantRepository;
    private final ReservationPolicyRepository policyRepository;
    private final SlotService slotService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (memberRepository.count() > 0) {
            return;
        }

        memberRepository.save(Member.builder()
                .email("guest@tably.com")
                .password(passwordEncoder.encode(SEED_PASSWORD))
                .name("김지현")
                .role(Role.GUEST)
                .build());
        Member owner = memberRepository.save(Member.builder()
                .email("owner@tably.com")
                .password(passwordEncoder.encode(SEED_PASSWORD))
                .name("박성호")
                .role(Role.OWNER)
                .build());

        Restaurant sushiJun = restaurantRepository.save(Restaurant.builder()
                .owner(owner).name("스시 준").build());
        policyRepository.save(ReservationPolicy.builder()
                .restaurant(sushiJun)
                .depositPerPerson(20000)
                .refundRule("7:100,3:50,1:0")
                .openRule("MONTHLY:1:10:00")
                .tablesPerTime(4)
                .slotTimes("18:00,20:30")
                .build());

        Restaurant bistro = restaurantRepository.save(Restaurant.builder()
                .owner(owner).name("비스트로 하늘").build());
        policyRepository.save(ReservationPolicy.builder()
                .restaurant(bistro)
                .depositPerPerson(10000)
                .refundRule("3:100,1:50")
                .openRule("MONTHLY:1:10:00")
                .tablesPerTime(6)
                .slotTimes("12:00,18:30")
                .build());

        String nextMonth = YearMonth.now().plusMonths(1).toString();
        int sushiSlots = slotService.generateMonthlySlots(owner.getId(), sushiJun.getId(),
                new SlotGenerateRequestDto(nextMonth));
        int bistroSlots = slotService.generateMonthlySlots(owner.getId(), bistro.getId(),
                new SlotGenerateRequestDto(nextMonth));

        log.info("시드 데이터 생성 완료 — 회원 2명(guest/owner@tably.com, pw: {}), 식당 2곳, {} 슬롯 {}+{}개",
                SEED_PASSWORD, nextMonth, sushiSlots, bistroSlots);
    }
}
