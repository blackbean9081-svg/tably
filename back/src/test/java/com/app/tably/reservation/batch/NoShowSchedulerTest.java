package com.app.tably.reservation.batch;

import com.app.tably.reservation.entity.ReservationStatus;
import com.app.tably.reservation.repository.ReservationRepository;
import com.app.tably.reservation.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class NoShowSchedulerTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private NoShowScheduler noShowScheduler;

    @Test
    @DisplayName("한 건의 실패가 배치 전체를 중단시키지 않는다 (S5 조건 6)")
    void processNoShows_continuesOnFailure() {
        given(reservationRepository.findIdsByStatusAndSlotBefore(
                eq(ReservationStatus.CONFIRMED), any(LocalDate.class), any(LocalTime.class)))
                .willReturn(List.of(1L, 2L, 3L));
        given(reservationService.markNoShow(1L)).willReturn(true);
        given(reservationService.markNoShow(2L)).willThrow(new RuntimeException("DB 일시 오류"));
        given(reservationService.markNoShow(3L)).willReturn(false);   // 방문 처리와 경합 패배 — 스킵

        noShowScheduler.processNoShows();

        then(reservationService).should().markNoShow(1L);
        then(reservationService).should().markNoShow(2L);
        then(reservationService).should().markNoShow(3L);
    }

    @Test
    @DisplayName("대상이 없으면 아무 일도 하지 않는다")
    void processNoShows_noCandidates() {
        given(reservationRepository.findIdsByStatusAndSlotBefore(
                eq(ReservationStatus.CONFIRMED), any(LocalDate.class), any(LocalTime.class)))
                .willReturn(List.of());

        noShowScheduler.processNoShows();

        then(reservationService).shouldHaveNoInteractions();
    }
}
