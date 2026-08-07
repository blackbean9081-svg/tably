package com.app.tably.payment.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Component
public class RefundCalculator {

    public enum CancelCause {
        USER,   // 손님 귀책 — 정책의 시점별 환불율 적용
        SHOP    // 식당 귀책 — 정책 무시, 전액 환불 (S8)
    }

    /**
     * FR-08: 시점별·귀책별 환불율(%).
     * refundRule("7:100,3:50,1:0")을 기준 일수 내림차순으로 훑어,
     * 남은 일수(방문일 - 취소일, 날짜 기준) 이상인 첫 구간의 환불율을 적용한다.
     * 어떤 구간에도 못 미치면(당일·직전) 0%. 식당 귀책은 정책 무시 100% (S8).
     * 환불액과 별도로 노출하는 이유: 사전 고지(S4)에서 "환불율 50%"를 함께 보여주기 위함.
     */
    public int rate(String refundRule, LocalDate visitDate, LocalDate cancelDate, CancelCause cause) {
        if (cause == CancelCause.SHOP) {
            return 100;
        }
        long daysLeft = ChronoUnit.DAYS.between(cancelDate, visitDate);
        for (RuleEntry entry : parse(refundRule)) {
            if (daysLeft >= entry.days()) {
                return entry.percent();
            }
        }
        return 0;
    }

    /**
     * FR-08: 시점별·귀책별 환불액 계산 — rate()의 환불율을 결제액에 적용한다.
     */
    public int calculate(String refundRule, LocalDate visitDate, LocalDate cancelDate,
                         int paidAmount, CancelCause cause) {
        long refund = (long) paidAmount * rate(refundRule, visitDate, cancelDate, cause) / 100;
        return (int) Math.clamp(refund, 0, paidAmount);
    }

    private List<RuleEntry> parse(String refundRule) {
        try {
            return Arrays.stream(refundRule.split(","))
                    .map(token -> {
                        String[] parts = token.split(":");
                        if (parts.length != 2) {
                            throw new IllegalArgumentException(token);
                        }
                        int days = Integer.parseInt(parts[0].strip());
                        int percent = Integer.parseInt(parts[1].strip());
                        if (days < 0 || percent < 0 || percent > 100) {
                            throw new IllegalArgumentException(token);
                        }
                        return new RuleEntry(days, percent);
                    })
                    .sorted(Comparator.comparingInt(RuleEntry::days).reversed())
                    .toList();
        } catch (RuntimeException e) {
            // 깨진 규칙을 조용히 0원 처리하면 분쟁 근거가 사라진다 (조건 6) — 즉시 실패로 드러낸다
            throw new IllegalStateException("환불 규칙 형식 오류: " + refundRule, e);
        }
    }

    private record RuleEntry(int days, int percent) {
    }
}
