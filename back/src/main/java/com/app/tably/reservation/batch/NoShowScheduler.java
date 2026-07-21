package com.app.tably.reservation.batch;

import com.app.tably.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NoShowScheduler {

    private final ReservationRepository reservationRepository;

    /*
     * TODO [핵심영역 5 — 노쇼 자동 전환 배치] 개발자 본인이 구현할 것. Claude Code 구현 금지.
     *
     * 이 메서드가 만족해야 할 조건 (S5, FR-12):
     *  1. 대상: CONFIRMED 상태이면서 "슬롯 시각 + 30분"이 지난 예약 → NO_SHOW 전환
     *     (slot_date + slot_time 기준 — 조회 쿼리도 직접 설계할 것)
     *  2. 전환은 반드시 상태 전이 검증(핵심영역 4)을 거친다
     *  3. 사장의 "방문 완료" 처리와 경합할 수 있다 — 배치가 읽은 뒤 VISITED로 바뀐 예약을
     *     NO_SHOW로 덮어쓰면 안 된다 (S5의 노쇼 오처리 분쟁이 정확히 이 사고)
     *  4. 배치가 중간에 죽어도 재실행 시 이미 처리된 건은 건너뛴다 (멱등)
     *  5. 노쇼 확정은 "몰수 끝"이 아니다 — 당일 자정까지 사장 정정(→VISITED),
     *     이의신청 인용(→NO_SHOW_REVOKED) 경로가 열려 있어야 한다
     *  6. 건별 실패가 전체 배치를 중단시키지 않는다 (실패 건 기록 후 계속)
     *
     * 구현 후 @Scheduled(fixedDelay = 60_000) 을 붙여 활성화할 것.
     * (지금 활성화하면 매분 UnsupportedOperationException 로그가 쌓여서 비활성 상태로 둔다)
     */
    public void processNoShows() {
        throw new UnsupportedOperationException("핵심영역 5 — 노쇼 자동 전환 배치 미구현");
    }
}
