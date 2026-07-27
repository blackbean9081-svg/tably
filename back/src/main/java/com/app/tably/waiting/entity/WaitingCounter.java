package com.app.tably.waiting.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 식당별 대기번호 카운터 (핵심영역 6, 접근 a).
 * 행을 PESSIMISTIC_WRITE로 잠근 뒤 +1 — 동시 등록이 이 행 위에서 직렬화되므로
 * 번호 중복·누락이 없다. 조회(순번 폴링)는 이 행을 건드리지 않는다.
 * Redis 전환(2막) 시 INCR로 대체되는 지점.
 */
@Entity
@Table(name = "waiting_counter")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "restaurant_id", nullable = false, unique = true)
    private Long restaurantId;

    @Column(name = "last_no", nullable = false)
    private int lastNo;

    public WaitingCounter(Long restaurantId) {
        this.restaurantId = restaurantId;
        this.lastNo = 0;
    }

    public int issueNext() {
        return ++lastNo;
    }
}
