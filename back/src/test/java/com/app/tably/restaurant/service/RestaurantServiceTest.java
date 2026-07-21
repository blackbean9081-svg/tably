package com.app.tably.restaurant.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.restaurant.dto.PolicyRequestDto;
import com.app.tably.restaurant.dto.PolicyResponseDto;
import com.app.tably.restaurant.dto.RestaurantCreateRequestDto;
import com.app.tably.restaurant.entity.ReservationPolicy;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.ReservationPolicyRepository;
import com.app.tably.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private ReservationPolicyRepository policyRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private RestaurantService restaurantService;

    private Member owner(Long id) {
        return Member.builder().id(id).email("owner@tably.com").password("pw").name("박성호").role(Role.OWNER).build();
    }

    private Restaurant restaurant(Long id, Member owner) {
        return Restaurant.builder().id(id).owner(owner).name("스시 준").build();
    }

    private PolicyRequestDto policyRequest() {
        return new PolicyRequestDto(20000, "7:100,3:50,1:0", "MONTHLY:1:10:00", 4, "18:00,20:30");
    }

    @Test
    @DisplayName("식당 등록 성공")
    void create_success() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(owner(1L)));
        given(restaurantRepository.save(any(Restaurant.class))).willReturn(restaurant(10L, owner(1L)));

        Long id = restaurantService.create(1L, new RestaurantCreateRequestDto("스시 준"));

        assertThat(id).isEqualTo(10L);
    }

    @Test
    @DisplayName("정책 신규 등록 — 기존 정책이 없으면 새로 저장한다")
    void upsertPolicy_create() {
        Restaurant restaurant = restaurant(10L, owner(1L));
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant));
        given(policyRepository.findByRestaurantId(10L)).willReturn(Optional.empty());
        given(policyRepository.save(any(ReservationPolicy.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PolicyResponseDto response = restaurantService.upsertPolicy(1L, 10L, policyRequest());

        assertThat(response.depositPerPerson()).isEqualTo(20000);
        assertThat(response.slotTimes()).isEqualTo("18:00,20:30");
    }

    @Test
    @DisplayName("정책 수정 — 기존 정책이 있으면 save 없이 필드만 갱신한다")
    void upsertPolicy_update() {
        Restaurant restaurant = restaurant(10L, owner(1L));
        ReservationPolicy existing = ReservationPolicy.builder()
                .id(100L).restaurant(restaurant)
                .depositPerPerson(10000).refundRule("7:100").openRule("MONTHLY:1:10:00")
                .tablesPerTime(2).slotTimes("18:00")
                .build();
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant));
        given(policyRepository.findByRestaurantId(10L)).willReturn(Optional.of(existing));

        PolicyResponseDto response = restaurantService.upsertPolicy(1L, 10L, policyRequest());

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.depositPerPerson()).isEqualTo(20000);
        verify(policyRepository, never()).save(any());
    }

    @Test
    @DisplayName("남의 식당 정책 수정은 NOT_RESTAURANT_OWNER 예외")
    void upsertPolicy_notOwner() {
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant(10L, owner(1L))));

        assertThatThrownBy(() -> restaurantService.upsertPolicy(2L, 10L, policyRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_RESTAURANT_OWNER);
    }

    @Test
    @DisplayName("정책 없는 식당 조회는 POLICY_NOT_FOUND 예외")
    void findPolicy_notFound() {
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant(10L, owner(1L))));
        given(policyRepository.findByRestaurantId(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.findPolicy(10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POLICY_NOT_FOUND);
    }
}
