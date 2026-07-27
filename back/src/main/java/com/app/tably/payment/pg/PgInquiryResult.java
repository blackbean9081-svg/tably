package com.app.tably.payment.pg;

// FR-07: UNKNOWN 재확인 응답 — 승인/실패의 확정 답만 담는다 (미확정이면 PgTimeoutException으로 다음 주기 이월)
public record PgInquiryResult(boolean approved, String pgTxId) {

    public static PgInquiryResult approved(String pgTxId) {
        return new PgInquiryResult(true, pgTxId);
    }

    public static PgInquiryResult failed() {
        return new PgInquiryResult(false, null);
    }
}
