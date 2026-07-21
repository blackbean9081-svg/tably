package com.app.tably.reservation.entity;

import com.app.tably.member.entity.Member;
import com.app.tably.slot.entity.Slot;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private Slot slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "held_at", nullable = false)
    private LocalDateTime heldAt;

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }

    /**
     * 상태를 바꾸기 전에 반드시 ReservationService.validateTransition(핵심영역 4)을 거칠 것.
     * 이 메서드는 검증 없이 값만 바꾼다.
     */
    public void changeStatus(ReservationStatus next) {
        this.status = next;
    }
}
