package com.app.tably.payment.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class RefundCalculator {

    public enum CancelCause {
        USER,   // 손님 귀책 — 정책의 시점별 환불율 적용
        SHOP    // 식당 귀책 — 정책 무시, 전액 환불 (S8)
    }

    /*
     * TODO [핵심영역 3 — 환불 금액 계산 (시점별·귀책별)] 개발자 본인이 구현할 것. Claude Code 구현 금지.
     *
     * 이 메서드가 만족해야 할 조건 (S4, S8, FR-08):
     *  1. cause == SHOP 이면 refundRule 무시하고 무조건 전액(paidAmount) 환불
     *  2. cause == USER 이면 refundRule("7:100,3:50,1:0" = 일수:환불율%)을 파싱해
     *     "방문일 - 취소일"의 남은 일수가 가장 큰 기준 일수 이상이면 그 환불율 적용
     *     예) 7일 전 취소 → 100%, 5일 전 → 50%, 3일 전 → 50%, 1일 전~당일 → 0%
     *  3. 경계 조건을 명확히: "7일 전"의 기준은 날짜 차이 >= 7 (시간 아님, 날짜 기준)
     *  4. 환불율 구간에 없는 잔여 일수(예: 규칙 최소 기준일 미만)는 0% 처리
     *  5. 결과는 원 단위 정수, 음수 불가, paidAmount 초과 불가
     *  6. refundRule 형식이 깨져 있으면 예외 — 조용히 0원 처리하면 CS 폭탄 (분쟁 근거 소실)
     */
    public int calculate(String refundRule, LocalDate visitDate, LocalDate cancelDate,
                         int paidAmount, CancelCause cause) {
        throw new UnsupportedOperationException("핵심영역 3 — 환불 금액 계산 미구현");
    }
}
