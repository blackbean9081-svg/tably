package com.app.tably.payment.pg;

// PG 응답 미수신 — 승인됐을 수도 있으므로 실패로 단정하면 안 된다 (UNKNOWN 기록 대상)
public class PgTimeoutException extends RuntimeException {

    public PgTimeoutException() {
        super("PG 응답 시간 초과");
    }
}
