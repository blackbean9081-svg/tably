package com.app.tably.restaurant.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reservation_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReservationPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false, unique = true)
    private Restaurant restaurant;

    @Column(name = "deposit_per_person", nullable = false)
    private int depositPerPerson;

    // "일수:환불율(%)" 콤마 구분, 예: "7:100,3:50,1:0" — 파싱·계산은 환불 계산 로직(핵심영역 3) 담당
    @Column(name = "refund_rule", nullable = false)
    private String refundRule;

    // 예약 오픈 방식, 예: "MONTHLY:1:10:00" (매월 1일 10:00에 다음 달 오픈)
    @Column(name = "open_rule", nullable = false)
    private String openRule;

    @Column(name = "tables_per_time", nullable = false)
    private int tablesPerTime;

    // 운영 타임 "HH:mm" 콤마 구분, 예: "18:00,20:30" — 슬롯 일괄 생성(FR-02)의 입력
    @Column(name = "slot_times", nullable = false)
    private String slotTimes;

    public void update(int depositPerPerson, String refundRule, String openRule,
                       int tablesPerTime, String slotTimes) {
        this.depositPerPerson = depositPerPerson;
        this.refundRule = refundRule;
        this.openRule = openRule;
        this.tablesPerTime = tablesPerTime;
        this.slotTimes = slotTimes;
    }
}
