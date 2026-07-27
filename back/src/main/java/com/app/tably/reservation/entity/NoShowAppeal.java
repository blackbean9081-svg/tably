package com.app.tably.reservation.entity;

import com.app.tably.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * S5/FR-13: 노쇼 이의신청. 노쇼는 "몰수 끝"이 아니라 번복 가능한 상태 —
 * 인용되면 예약이 NO_SHOW_REVOKED로 전이되고 예약금이 환불된다.
 * 신청·심사 이력은 분쟁 근거이므로 행으로 남긴다.
 */
@Entity
@Table(name = "no_show_appeal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class NoShowAppeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppealStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public boolean isOpen() {
        return status == AppealStatus.OPEN;
    }

    public void accept(LocalDateTime now) {
        this.status = AppealStatus.ACCEPTED;
        this.decidedAt = now;
    }

    public void reject(LocalDateTime now) {
        this.status = AppealStatus.REJECTED;
        this.decidedAt = now;
    }
}
