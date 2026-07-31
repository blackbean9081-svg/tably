package com.app.tably.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundCalculatorTest {

    private final RefundCalculator calculator = new RefundCalculator();

    private static final String RULE = "7:100,3:50,1:0";
    private static final LocalDate VISIT = LocalDate.of(2026, 8, 15);

    @Test
    @DisplayName("손님 귀책 — 7일 전 100%, 5일 전 50%, 3일 전 50%, 1일 전 0%, 당일 0%")
    void calculate_userCause() {
        assertThat(calculator.calculate(RULE, VISIT, VISIT.minusDays(7), 40000, RefundCalculator.CancelCause.USER)).isEqualTo(40000);
        assertThat(calculator.calculate(RULE, VISIT, VISIT.minusDays(5), 40000, RefundCalculator.CancelCause.USER)).isEqualTo(20000);
        assertThat(calculator.calculate(RULE, VISIT, VISIT.minusDays(3), 40000, RefundCalculator.CancelCause.USER)).isEqualTo(20000);
        assertThat(calculator.calculate(RULE, VISIT, VISIT.minusDays(1), 40000, RefundCalculator.CancelCause.USER)).isZero();
        assertThat(calculator.calculate(RULE, VISIT, VISIT, 40000, RefundCalculator.CancelCause.USER)).isZero();
    }

    @Test
    @DisplayName("식당 귀책은 시점 무관 전액 환불 (S8)")
    void calculate_shopCause() {
        assertThat(calculator.calculate(RULE, VISIT, VISIT, 40000, RefundCalculator.CancelCause.SHOP)).isEqualTo(40000);
        assertThat(calculator.calculate(RULE, VISIT, VISIT.minusDays(10), 40000, RefundCalculator.CancelCause.SHOP)).isEqualTo(40000);
    }

    @Test
    @DisplayName("방문일이 지난 뒤의 취소(음수 잔여 일수)는 0% 처리")
    void calculate_afterVisitDate() {
        assertThat(calculator.calculate(RULE, VISIT, VISIT.plusDays(1), 40000, RefundCalculator.CancelCause.USER)).isZero();
    }

    @Test
    @DisplayName("규칙 순서가 뒤섞여 있어도 기준 일수 내림차순으로 매칭한다")
    void calculate_unorderedRule() {
        assertThat(calculator.calculate("1:0,7:100,3:50", VISIT, VISIT.minusDays(8), 40000,
                RefundCalculator.CancelCause.USER)).isEqualTo(40000);
    }

    @Test
    @DisplayName("rate — 사전 고지용 환불율: 시점별 %를 그대로 노출, 식당 귀책은 100")
    void rate_forPreview() {
        assertThat(calculator.rate(RULE, VISIT, VISIT.minusDays(7), RefundCalculator.CancelCause.USER)).isEqualTo(100);
        assertThat(calculator.rate(RULE, VISIT, VISIT.minusDays(3), RefundCalculator.CancelCause.USER)).isEqualTo(50);
        assertThat(calculator.rate(RULE, VISIT, VISIT, RefundCalculator.CancelCause.USER)).isZero();
        assertThat(calculator.rate(RULE, VISIT, VISIT, RefundCalculator.CancelCause.SHOP)).isEqualTo(100);
    }

    @Test
    @DisplayName("깨진 규칙은 조용히 0원 처리하지 않고 예외 — 분쟁 근거 소실 방지 (조건 6)")
    void calculate_malformedRule() {
        assertThatThrownBy(() -> calculator.calculate("7-100", VISIT, VISIT.minusDays(7), 40000,
                RefundCalculator.CancelCause.USER))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> calculator.calculate("7:abc", VISIT, VISIT.minusDays(7), 40000,
                RefundCalculator.CancelCause.USER))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> calculator.calculate("7:150", VISIT, VISIT.minusDays(7), 40000,
                RefundCalculator.CancelCause.USER))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> calculator.calculate("", VISIT, VISIT.minusDays(7), 40000,
                RefundCalculator.CancelCause.USER))
                .isInstanceOf(IllegalStateException.class);
    }
}
