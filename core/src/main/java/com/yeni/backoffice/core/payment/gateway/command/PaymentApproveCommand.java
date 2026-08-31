package com.yeni.backoffice.core.payment.gateway.command;

import com.yeni.backoffice.core.payment.enums.PgProvider;

import java.math.BigDecimal;

public record PaymentApproveCommand(
        PgProvider pgProvider,
        String mid,
        String orderNo,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        String channelType,
        String storeCode,
        String paymentMethod,
        Long storeId,
        String productName
) {
    public PaymentApproveCommand(
            PgProvider pgProvider, String mid, String orderNo, BigDecimal amount,
            String currency, String idempotencyKey, String channelType,
            String storeCode, String paymentMethod, Long storeId) {
        this(pgProvider, mid, orderNo, amount, currency, idempotencyKey,
                channelType, storeCode, paymentMethod, storeId, null);
    }

    public PaymentApproveCommand(
            PgProvider pgProvider, String mid, String orderNo, BigDecimal amount,
            String currency, String idempotencyKey, String channelType,
            String storeCode, String paymentMethod) {
        this(pgProvider, mid, orderNo, amount, currency, idempotencyKey,
                channelType, storeCode, paymentMethod, null, null);
    }
}
