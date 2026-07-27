package com.app.tably.payment.pg;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 토스페이먼츠 테스트 연동 전까지의 대역.
 * orderKey 접두어로 거절/타임아웃 시나리오를 재현할 수 있다 — 로컬 시연·k6에서
 * "pg-decline-...", "pg-timeout-..." 키를 보내면 해당 실패 경로가 실행된다.
 */
@Component
public class MockPgClient implements PgClient {

    @Override
    public String approve(String orderKey, int amount) {
        if (orderKey.startsWith("pg-timeout")) {
            throw new PgTimeoutException();
        }
        if (orderKey.startsWith("pg-decline")) {
            throw new PgDeclinedException("카드 승인 거절 (모의 응답)");
        }
        return "mock-pay-" + UUID.randomUUID();
    }

    @Override
    public String cancel(String pgTxId, int amount) {
        return "mock-cancel-" + UUID.randomUUID();
    }

    @Override
    public PgInquiryResult inquire(String orderKey) {
        // "pg-timeout-approved-..." 키는 타임아웃했지만 실제로는 승인됐던 시나리오 재현 (S3의 카드사 문자 케이스)
        if (orderKey.startsWith("pg-timeout-approved")) {
            return PgInquiryResult.approved("mock-recovered-" + UUID.randomUUID());
        }
        return PgInquiryResult.failed();
    }
}
