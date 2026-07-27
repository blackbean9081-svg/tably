package com.app.tably.notification.service;

import com.app.tably.notification.entity.Notification;
import com.app.tably.notification.entity.NotificationStatus;
import com.app.tably.notification.entity.NotificationType;
import com.app.tably.notification.event.NotificationEvent;
import com.app.tably.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationSender notificationSender;

    @InjectMocks
    private NotificationService notificationService;

    private Notification pending(Long id) {
        return Notification.builder()
                .id(id).eventId("ev-1").memberId(1L)
                .type(NotificationType.RESERVATION_CONFIRMED)
                .message("예약이 확정되었습니다.")
                .status(NotificationStatus.PENDING)
                .attemptCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("이벤트 수신 시 발행 트랜잭션 안에서 PENDING 기록이 저장된다 (FR-21)")
    void record() {
        var event = NotificationEvent.of(1L, NotificationType.RESERVATION_CONFIRMED, "예약이 확정되었습니다.");

        notificationService.record(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should().save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(captor.getValue().getEventId()).isEqualTo(event.eventId());
        assertThat(captor.getValue().getMemberId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("커밋 후 발송 성공 — SENT + 발송 시각 기록")
    void dispatchAfterCommit_success() {
        Notification notification = pending(1L);
        given(notificationRepository.findByEventId("ev-1")).willReturn(Optional.of(notification));

        notificationService.dispatchAfterCommit(
                new NotificationEvent("ev-1", 1L, NotificationType.RESERVATION_CONFIRMED, "예약이 확정되었습니다."));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
        assertThat(notification.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("발송 실패 — 예외를 삼키고 FAILED로 남겨 재시도 배치가 잇는다")
    void redeliver_failure() {
        Notification notification = pending(1L);
        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));
        willThrow(new IllegalStateException("발송 실패")).given(notificationSender).send(any());

        boolean sent = notificationService.redeliver(1L);

        assertThat(sent).isFalse();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 SENT인 알림 재발송은 건너뛴다 (재시도 경합 멱등)")
    void redeliver_alreadySent() {
        Notification notification = pending(1L);
        notification.markSent(LocalDateTime.now());
        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        boolean sent = notificationService.redeliver(1L);

        assertThat(sent).isTrue();
        then(notificationSender).should(never()).send(any());
        assertThat(notification.getAttemptCount()).isEqualTo(1);
    }
}
