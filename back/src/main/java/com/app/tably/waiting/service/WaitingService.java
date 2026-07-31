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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final WaitingRankIndex rankIndex;

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
        // 인덱스 반영은 커밋 후 — 롤백된 등록이 유령 멤버로 남아 남의 앞 팀 수를 부풀리지 않게
        afterCommit(() -> rankIndex.add(restaurant.getId(), waiting.getId(), waitingNo));
        return WaitingResponseDto.of(waiting, aheadCount(waiting));
    }

    /**
     * FR-15: 순번 조회 — 모두가 계속 새로고침하는 지점.
     * 1막은 count 쿼리, 2막(rank-mode=redis)은 Sorted Set 인덱스가 답하고 유실 시 DB로 폴백한다.
     */
    public WaitingResponseDto getMyWaiting(Long memberId, Long waitingId) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));
        if (!waiting.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_WAITING_OWNER);
        }
        long ahead = waiting.getStatus() == WaitingStatus.WAITING
                ? rankIndex.aheadCount(waiting.getRestaurant().getId(), waiting.getId(), waiting.getWaitingNo())
                        .orElseGet(() -> aheadCount(waiting))
                : aheadCount(waiting);
        return WaitingResponseDto.of(waiting, ahead);
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
        // CALLED는 대기(WAITING) 집합에서 빠진다 — 뒤 팀들의 앞 팀 수가 즉시 줄어든다
        afterCommit(() -> rankIndex.remove(restaurantId, next.getId()));
        eventPublisher.publishEvent(NotificationEvent.of(next.getMember().getId(),
                NotificationType.WAITING_CALLED,
                "%s 입장 순서입니다 (대기 %d번). 10분 내 도착을 확인해주세요.".formatted(
                        restaurant.getName(), next.getWaitingNo())));
        return WaitingResponseDto.of(next, 0L);
    }

    @Transactional
    public WaitingResponseDto seat(Long ownerId, Long waitingId) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));
        if (!waiting.getRestaurant().isOwnedBy(ownerId)) {
            throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
        }
        boolean seated = waitingRepository.updateStatusIfCurrentIn(
                waitingId, List.of(WaitingStatus.CALLED), WaitingStatus.SEATED) == 1;
        if (!seated) {
            throw new BusinessException(ErrorCode.WAITING_NOT_CALLED);
        }
        return WaitingResponseDto.of(waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND)), 0L);
    }

    @Transactional
    public void cancel(Long memberId, Long waitingId) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));
        if (!waiting.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_WAITING_OWNER);
        }
        boolean canceled = waitingRepository.updateStatusIfCurrentIn(
                waitingId, ACTIVE_STATUSES, WaitingStatus.CANCELED) == 1;
        if (!canceled) {
            throw new BusinessException(ErrorCode.WAITING_ALREADY_CLOSED);
        }
        afterCommit(() -> rankIndex.remove(waiting.getRestaurant().getId(), waitingId));
    }

    /**
     * FR-16: 호출 후 10분 미도착 자동 만료 — 스케줄러 진입점.
     */
    @Transactional
    public int expireOverdueCalls() {
        LocalDateTime threshold = LocalDateTime.now().minus(CALL_TIMEOUT);
        int expired = waitingRepository.updateStatusAllCalledBefore(
                WaitingStatus.CALLED, threshold, WaitingStatus.EXPIRED);
        if (expired > 0) {
            log.info("호출 만료 처리 {}건", expired);
        }
        return expired;
    }

    private Long aheadCount(Waiting waiting) {
        return waitingRepository.countByRestaurantIdAndStatusAndWaitingNoLessThan(
                waiting.getRestaurant().getId(), WaitingStatus.WAITING, waiting.getWaitingNo());
    }

    // 인덱스 갱신은 커밋 후에만 — 롤백된 변경이 읽기 모델에 반영되면 DB(진실 원천)와 어긋난다
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
