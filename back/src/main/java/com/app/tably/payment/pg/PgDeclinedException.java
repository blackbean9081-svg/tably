package com.app.tably.payment.pg;

// PG가 명시적으로 거절 — 실패 확정(FAILED)으로 기록해도 안전하다
public class PgDeclinedException extends RuntimeException {

    public PgDeclinedException(String message) {
        super(message);
    }
}
