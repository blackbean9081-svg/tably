package com.app.tably.slot.entity;

import com.app.tably.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "slot", uniqueConstraints = {
        // 같은 식당·날짜·타임·테이블 슬롯 중복 생성 방지 (생성 멱등성의 최후 방어선)
        @UniqueConstraint(name = "uk_slot_restaurant_date_time_table",
                columnNames = {"restaurant_id", "slot_date", "slot_time", "table_no"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;

    @Column(name = "table_no", nullable = false)
    private int tableNo;

    // 슬롯의 열림/닫힘만 관리 — 예약 여부는 reservation.status가 단일 진실 (ERD 원칙 3)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status;

    public void close() {
        this.status = SlotStatus.CLOSED;
    }

    public void open() {
        this.status = SlotStatus.OPEN;
    }
}
