package com.app.tably.payment.pg;

/**
 * PG 연동 경계. 승인/취소 성공 시 PG 거래 id를 반환하고,
 * 거절은 PgDeclinedException, 응답 유실(타임아웃)은 PgTimeoutException으로 구분한다 —
 * 호출부가 "실패 확정"과 "모름(UNKNOWN)"을 다르게 기록해야 하기 때문 (S3).
 */
public interface PgClient {

    String approve(String orderKey, int amount);

    String cancel(String pgTxId, int amount);

    // FR-07: 응답 유실(UNKNOWN) 건의 실제 승인 여부를 PG에 재확인
    PgInquiryResult inquire(String orderKey);
}
