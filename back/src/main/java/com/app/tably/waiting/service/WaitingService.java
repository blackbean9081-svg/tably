package com.app.tably.waiting.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.notification.entity.NotificationType;
import com.app.tably.notification.event.NotificationEvent;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.RestaurantRepository;
import com.app.tably.waiting.dto.WaitingRegisterRequestDto;
import com.app.tably.waiting.dto.WaitingResponseDto;
import com.app.tably.waiting.entity.Waiting;
import com.app.tably.waiting.entity.WaitingCounter;
import com.app.tably.waiting.entity.WaitingStatus;
import com.app.tably.waiting.repository.WaitingCounterRepository;
import com.app.tably.waiting.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class WaitingService {

    // 호출 후 도착 확인 시한 (S6: 10분 내 도착 확인 없으면 순번이 넘어간다)
    public static final Duration CALL_TIMEOUT = Duration.ofMinutes(10);

    private static final List<WaitingStatus> ACTIVE_STATUSES =
            List.of(WaitingStatus.WAITING, WaitingStatus.CALLED);

    private final WaitingRepository waitingRepository;
    private final WaitingCounterRepository waitingCounterRepository;
    private final RestaurantRepository restaurantRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 핵심영역 6 — 식당별 카운터 행 + 비관적 락 (접근 a).
     * findTop 후 +1은 check-then-act 경합으로 중복 번호가 나오므로,
     * 카운터 행을 잠가 발급을 직렬화한다. 등록은 드물고 조회(폴링)가 잦은 도메인이라
     * 조회 경로에 비용을 얹지 않는 쪽을 택했다. register 트랜잭션에 참여하므로
     * 저장이 실패하면 번호 증가도 함께 롤백된다 (번호 없는 대기·누락 번호 없음).
     */
    protected int issueWaitingNo(Long restaurantId) {
        waitingCounterRepository.insertIfAbsent(restaurantId);
        WaitingCounter counter = waitingCounterRepository.findWithLockByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalStateException(
                        "waiting_counter 행이 없습니다 — restaurant " + restaurantId));
        return counter.issueNext();
    }

    /**
     * FR-14: 웨이팅 등록. 검증·저장 흐름은 구현 — 번호 발급만 핵심영역 6에 위임.
     */
    @Transactional
    public WaitingResponseDto register(Long memberId, WaitingRegisterRequestDto request) {
        Restaurant restaurant = restaurantRepository.findById(request.restaurantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (waitingRepository.existsByRestaurantIdAndMemberIdAndStatusIn(
                restaurant.getId(), memberId, ACTIVE_STATUSES)) {
            throw new BusinessException(ErrorCode.ALREADY_WAITING);
        }

        int waitingNo = issueWaitingNo(restaurant.getId());

        Waiting waiting = waitingRepository.save(Waiting.builder()
                .restaurant(restaurant)
                .member(member)
                .waitingNo(waitingNo)
                .status(WaitingStatus.WAITING)
                .build());
        return WaitingResponseDto.of(waiting, aheadCount(waiting));
    }

    /**
     * FR-15: 순번 조회 — 모두가 계속 새로고침하는 지점이라 count 쿼리 하나로 가볍게.
     */
    public WaitingResponseDto getMyWaiting(Long memberId, Long waitingId) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));
        if (!waiting.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_WAITING_OWNER);
        }
        return WaitingResponseDto.of(waiting, aheadCount(waiting));
    }

    /**
     * FR-16: 사장의 "다음 팀 호출" — WAITING 최소 번호를 CALLED로.
     */
    @Transactional
    public WaitingResponseDto callNext(Long ownerId, Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        if (!restaurant.isOwnedBy(ownerId)) {
            throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
        }
        Waiting next = waitingRepository
                .findFirstByRestaurantIdAndStatusOrderByWaitingNoAsc(restaurantId, WaitingStatus.WAITING)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_WAITING_TO_CALL));
        next.call(LocalDateTime.now());
        eventPublisher.publishEvent(NotificationEvent.of(next.getMember().getId(),
                NotificationType.WAITING_CALLED,
                "%s 입장 순서입니다 (대기 %d번). 10분 내 도착을 확인해주세요.".formatted(
                        restaurant.getName(), next.getWaitingNo())));
        return WaitingResponseDto.of(next, 0L);
    }

    /**
     * FR-16: 호출 후 10분 미도착 자동 만료 — 스케줄러 진입점.
     */
    @Transactional
    public int expireOverdueCalls() {
        LocalDateTime threshold = LocalDateTime.now().minus(CALL_TIMEOUT);
        List<Waiting> overdue = waitingRepository
                .findAllByStatusAndCalledAtBefore(WaitingStatus.CALLED, threshold);
        overdue.forEach(Waiting::expire);
        if (!overdue.isEmpty()) {
            log.info("호출 만료 처리 {}건", overdue.size());
        }
        return overdue.size();
    }

    private Long aheadCount(Waiting waiting) {
        return waitingRepository.countByRestaurantIdAndStatusAndWaitingNoLessThan(
                waiting.getRestaurant().getId(), WaitingStatus.WAITING, waiting.getWaitingNo());
    }
}
