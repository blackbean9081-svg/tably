package com.app.tably.waiting.entity;

import com.app.tably.member.entity.Member;
import com.app.tably.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "waiting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Waiting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "waiting_no", nullable = false)
    private int waitingNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WaitingStatus status;

    @Column(name = "called_at")
    private LocalDateTime calledAt;

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }

    public void call(LocalDateTime now) {
        this.status = WaitingStatus.CALLED;
        this.calledAt = now;
    }

    public void expire() {
        this.status = WaitingStatus.EXPIRED;
    }

    public void seat() {
        this.status = WaitingStatus.SEATED;
    }

    public void cancel() {
        this.status = WaitingStatus.CANCELED;
    }
}
