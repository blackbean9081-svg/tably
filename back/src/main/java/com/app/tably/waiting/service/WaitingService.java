package com.app.tably.waiting.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.RestaurantRepository;
import com.app.tably.waiting.dto.WaitingRegisterRequestDto;
import com.app.tably.waiting.dto.WaitingResponseDto;
import com.app.tably.waiting.entity.Waiting;
import com.app.tably.waiting.entity.WaitingStatus;
import com.app.tably.waiting.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final RestaurantRepository restaurantRepository;
    private final MemberRepository memberRepository;

    /*
     * TODO [핵심영역 6 — 대기번호 발급] 개발자 본인이 구현할 것. Claude Code 구현 금지.
     *
     * 이 메서드가 만족해야 할 조건 (S6, FR-14):
     *  1. 같은 식당에 동시 등록해도 대기번호는 중복·누락 없이 발급된다
     *     — findTopByRestaurantIdOrderByWaitingNoDesc 후 +1 하는 단순 구현은
     *       check-then-act 경합으로 중복 번호가 나온다. 이를 락/제약/시퀀스로 막을 것
     *  2. 번호는 식당별로 1부터 단조 증가 (운영일 단위 리셋 여부는 v1에서는 리셋 없음으로 둔다)
     *  3. 발급 실패 시 waiting 행이 남지 않아야 한다 (번호 없는 대기 금지)
     *  4. 조회(순번 폴링)가 매우 잦다 — 발급 방식이 조회 성능을 해치지 않을 것
     *  접근 후보: (a) 식당별 카운터 행 + 비관적 락 (b) DB 시퀀스/유니크 제약 재시도 (c) Redis 전환 시 INCR
     */
    protected int issueWaitingNo(Long restaurantId) {
        throw new UnsupportedOperationException("핵심영역 6 — 대기번호 발급 미구현");
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
