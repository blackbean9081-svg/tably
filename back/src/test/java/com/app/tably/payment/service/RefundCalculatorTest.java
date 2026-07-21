package com.app.tably.payment.service;

import org.junit.jupiter.api.Disabled;
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
    @DisplayName("환불 계산은 핵심영역 3 구현 전까지 UnsupportedOperationException")
    void calculate_notImplementedYet() {
        assertThatThrownBy(() -> calculator.calculate(RULE, VISIT, VISIT.minusDays(7), 40000,
                RefundCalculator.CancelCause.USER))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── 핵심영역 3 구현 후 활성화할 명세 테스트 ────────────────────────────

    @Test
    @Disabled("핵심영역 3(환불 계산) 구현 후 활성화")
    @DisplayName("[명세] 손님 귀책 — 7일 전 100%, 5일 전 50%, 3일 전 50%, 1일 전 0%, 당일 0%")
    void calculate_userCause_spec() {
        assertThat(calculator.calculate(RULE, VISIT, VISIT.minusDays(7), 40000, RefundCalculator.CancelCause.USER)).isEqualTo(40000);
        assertThat(calculator.calculate(RULE, VISIT, VISIT.minusDays(5), 40000, RefundCalculator.CancelCause.USER)).isEqualTo(20000);
        assertThat(calculator.calculate(RULE, VISIT, VISIT.minusDays(3), 40000, RefundCalculator.CancelCause.USER)).isEqualTo(20000);
        assertThat(calculator.calculate(RULE, VISIT, VISIT.minusDays(1), 40000, RefundCalculator.CancelCause.USER)).isZero();
        assertThat(calculator.calculate(RULE, VISIT, VISIT, 40000, RefundCalculator.CancelCause.USER)).isZero();
    }

    @Test
    @Disabled("핵심영역 3(환불 계산) 구현 후 활성화")
    @DisplayName("[명세] 식당 귀책은 시점 무관 전액 환불")
    void calculate_shopCause_spec() {
        assertThat(calculator.calculate(RULE, VISIT, VISIT, 40000, RefundCalculator.CancelCause.SHOP)).isEqualTo(40000);
    }
}
