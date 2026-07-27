package com.app.tably.settlement.dto;

// 정산 배치 실행 결과 요약 — settled: 정산서 생성, skipped: 대상 없음/이미 정산, failed: 오류 (재실행 대상)
public record SettlementRunResultDto(String month, int settled, int skipped, int failed) {
}
