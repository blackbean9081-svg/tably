package com.app.tably.slot.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.restaurant.entity.ReservationPolicy;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.ReservationPolicyRepository;
import com.app.tably.restaurant.repository.RestaurantRepository;
import com.app.tably.slot.dto.SlotGenerateRequestDto;
import com.app.tably.slot.entity.Slot;
import com.app.tably.slot.repository.SlotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private ReservationPolicyRepository policyRepository;

    @InjectMocks
    private SlotService slotService;

    private Restaurant restaurant(Long ownerId) {
        Member owner = Member.builder().id(ownerId).email("owner@tably.com")
                .password("pw").name("박성호").role(Role.OWNER).build();
        return Restaurant.builder().id(10L).owner(owner).name("스시 준").build();
    }

    private ReservationPolicy policy(Restaurant restaurant) {
        return ReservationPolicy.builder()
                .id(100L).restaurant(restaurant)
                .depositPerPerson(20000).refundRule("7:100,3:50,1:0")
                .openRule("MONTHLY:1:10:00").tablesPerTime(4).slotTimes("18:00,20:30")
                .build();
    }

    @Test
    @DisplayName("2026-08 슬롯 생성 = 31일 × 2타임 × 4테이블 = 248개")
    void generate_success() {
        Restaurant restaurant = restaurant(1L);
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant));
        given(policyRepository.findByRestaurantId(10L)).willReturn(Optional.of(policy(restaurant)));
        given(slotRepository.existsByRestaurantIdAndSlotDateBetween(anyLong(), any(), any())).willReturn(false);

        int count = slotService.generateMonthlySlots(1L, 10L, new SlotGenerateRequestDto("2026-08"));

        assertThat(count).isEqualTo(31 * 2 * 4);
        ArgumentCaptor<List<Slot>> captor = ArgumentCaptor.forClass(List.class);
        verify(slotRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(248);
        assertThat(captor.getValue().getFirst().getSlotDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(captor.getValue().getLast().getSlotDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("정기 휴무 요일은 슬롯을 생성하지 않는다 — 2026-08 월요일 5일 제외 = 208개 (S1)")
    void generate_skipsClosedDays() {
        Restaurant restaurant = restaurant(1L);
        ReservationPolicy policy = ReservationPolicy.builder()
                .id(100L).restaurant(restaurant)
                .depositPerPerson(20000).refundRule("7:100,3:50,1:0")
                .openRule("MONTHLY:1:10:00").tablesPerTime(4).slotTimes("18:00,20:30")
                .closedDays("MONDAY")
                .build();
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant));
        given(policyRepository.findByRestaurantId(10L)).willReturn(Optional.of(policy));
        given(slotRepository.existsByRestaurantIdAndSlotDateBetween(anyLong(), any(), any())).willReturn(false);

        int count = slotService.generateMonthlySlots(1L, 10L, new SlotGenerateRequestDto("2026-08"));

        assertThat(count).isEqualTo((31 - 5) * 2 * 4);
        ArgumentCaptor<List<Slot>> captor = ArgumentCaptor.forClass(List.class);
        verify(slotRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .noneMatch(slot -> slot.getSlotDate().getDayOfWeek() == DayOfWeek.MONDAY);
    }

    @Test
    @DisplayName("오픈 시각(매월 1일 10:00) 도래 — 다음 달 슬롯이 자동 생성된다 (FR-02)")
    void openDue_generatesNextMonth() {
        Restaurant restaurant = restaurant(1L);
        ReservationPolicy policy = policy(restaurant);
        given(policyRepository.findAll()).willReturn(List.of(policy));
        given(slotRepository.existsByRestaurantIdAndSlotDateBetween(anyLong(), any(), any())).willReturn(false);
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant));
        given(policyRepository.findByRestaurantId(10L)).willReturn(Optional.of(policy));

        int opened = slotService.openDueMonthlySlots(LocalDateTime.of(2026, 8, 1, 10, 0));

        assertThat(opened).isEqualTo(1);
        ArgumentCaptor<List<Slot>> captor = ArgumentCaptor.forClass(List.class);
        verify(slotRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(30 * 2 * 4);
        assertThat(captor.getValue().getFirst().getSlotDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("오픈 시각 전에는 자동 생성하지 않는다")
    void openDue_beforeOpenTime() {
        given(policyRepository.findAll()).willReturn(List.of(policy(restaurant(1L))));

        int opened = slotService.openDueMonthlySlots(LocalDateTime.of(2026, 8, 1, 9, 59));

        assertThat(opened).isZero();
        verify(slotRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("다음 달 슬롯이 이미 있으면 건너뛴다 — 매분 재실행 안전")
    void openDue_alreadyGenerated() {
        given(policyRepository.findAll()).willReturn(List.of(policy(restaurant(1L))));
        given(slotRepository.existsByRestaurantIdAndSlotDateBetween(anyLong(), any(), any())).willReturn(true);

        int opened = slotService.openDueMonthlySlots(LocalDateTime.of(2026, 8, 1, 10, 0));

        assertThat(opened).isZero();
        verify(slotRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("같은 월 재생성 요청은 SLOT_ALREADY_GENERATED 예외")
    void generate_duplicate() {
        Restaurant restaurant = restaurant(1L);
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant));
        given(policyRepository.findByRestaurantId(10L)).willReturn(Optional.of(policy(restaurant)));
        given(slotRepository.existsByRestaurantIdAndSlotDateBetween(anyLong(), any(), any())).willReturn(true);

        assertThatThrownBy(() -> slotService.generateMonthlySlots(1L, 10L, new SlotGenerateRequestDto("2026-08")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SLOT_ALREADY_GENERATED);
        verify(slotRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("남의 식당 슬롯 생성은 NOT_RESTAURANT_OWNER 예외")
    void generate_notOwner() {
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant(1L)));

        assertThatThrownBy(() -> slotService.generateMonthlySlots(2L, 10L, new SlotGenerateRequestDto("2026-08")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_RESTAURANT_OWNER);
    }

    @Test
    @DisplayName("정책 미설정 식당의 슬롯 생성은 POLICY_NOT_FOUND 예외")
    void generate_noPolicy() {
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant(1L)));
        given(policyRepository.findByRestaurantId(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> slotService.generateMonthlySlots(1L, 10L, new SlotGenerateRequestDto("2026-08")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POLICY_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 식당의 슬롯 조회는 RESTAURANT_NOT_FOUND 예외")
    void getSlots_restaurantNotFound() {
        given(restaurantRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> slotService.getSlots(99L, LocalDate.of(2026, 8, 15)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }
}
