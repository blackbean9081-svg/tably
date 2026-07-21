package com.app.tably.payment.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentType {

    PAY("예약금 결제"),
    REFUND("환불");

    private final String description;
}
