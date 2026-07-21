package com.app.tably.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // member
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),

    // restaurant
    RESTAURANT_NOT_FOUND(HttpStatus.NOT_FOUND, "식당을 찾을 수 없습니다."),
    POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "예약 정책이 설정되지 않은 식당입니다."),
    NOT_RESTAURANT_OWNER(HttpStatus.FORBIDDEN, "해당 식당의 사장만 가능한 작업입니다."),

    // slot
    SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "슬롯을 찾을 수 없습니다."),
    SLOT_ALREADY_GENERATED(HttpStatus.CONFLICT, "해당 월의 슬롯이 이미 생성되어 있습니다."),

    // reservation
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."),
    NOT_RESERVATION_OWNER(HttpStatus.FORBIDDEN, "본인의 예약만 처리할 수 있습니다."),
    SLOT_ALREADY_TAKEN(HttpStatus.CONFLICT, "방금 마감되었습니다."),
    SLOT_CLOSED(HttpStatus.CONFLICT, "예약할 수 없는 슬롯입니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "현재 상태에서 불가능한 처리입니다."),

    // payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 내역을 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 예약 정보와 일치하지 않습니다."),
    PAYMENT_TIME_EXPIRED(HttpStatus.CONFLICT, "결제 시한(10분)이 지났습니다.");

    private final HttpStatus status;
    private final String message;
}
