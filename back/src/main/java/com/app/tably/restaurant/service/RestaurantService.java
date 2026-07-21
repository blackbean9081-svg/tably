package com.app.tably.restaurant.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.restaurant.dto.PolicyRequestDto;
import com.app.tably.restaurant.dto.PolicyResponseDto;
import com.app.tably.restaurant.dto.RestaurantCreateRequestDto;
import com.app.tably.restaurant.dto.RestaurantResponseDto;
import com.app.tably.restaurant.entity.ReservationPolicy;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.ReservationPolicyRepository;
import com.app.tably.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final ReservationPolicyRepository policyRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Long create(Long ownerId, RestaurantCreateRequestDto request) {
        Member owner = memberRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Restaurant restaurant = Restaurant.builder()
                .owner(owner)
                .name(request.name())
                .build();
        return restaurantRepository.save(restaurant).getId();
    }

    public List<RestaurantResponseDto> findAll() {
        return restaurantRepository.findAll().stream()
                .map(RestaurantResponseDto::from)
                .toList();
    }

    public RestaurantResponseDto findOne(Long restaurantId) {
        return RestaurantResponseDto.from(getRestaurant(restaurantId));
    }

    @Transactional
    public PolicyResponseDto upsertPolicy(Long ownerId, Long restaurantId, PolicyRequestDto request) {
        Restaurant restaurant = getRestaurant(restaurantId);
        validateOwner(restaurant, ownerId);

        ReservationPolicy policy = policyRepository.findByRestaurantId(restaurantId)
                .map(existing -> {
                    existing.update(request.depositPerPerson(), request.refundRule(),
                            request.openRule(), request.tablesPerTime(), request.slotTimes());
                    return existing;
                })
                .orElseGet(() -> policyRepository.save(ReservationPolicy.builder()
                        .restaurant(restaurant)
                        .depositPerPerson(request.depositPerPerson())
                        .refundRule(request.refundRule())
                        .openRule(request.openRule())
                        .tablesPerTime(request.tablesPerTime())
                        .slotTimes(request.slotTimes())
                        .build()));
        return PolicyResponseDto.from(policy);
    }

    public PolicyResponseDto findPolicy(Long restaurantId) {
        getRestaurant(restaurantId);
        return policyRepository.findByRestaurantId(restaurantId)
                .map(PolicyResponseDto::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));
    }

    private Restaurant getRestaurant(Long restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    private void validateOwner(Restaurant restaurant, Long memberId) {
        if (!restaurant.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
        }
    }
}
