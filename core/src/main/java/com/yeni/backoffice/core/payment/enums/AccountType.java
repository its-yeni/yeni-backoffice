package com.yeni.backoffice.core.payment.enums;

/**
 * 계정과목 대분류. 정상잔액(normal balance)이 차변인지 대변인지를 함께 정의한다.
 * ASSET/EXPENSE 는 차변 증가, LIABILITY/EQUITY/REVENUE 는 대변 증가.
 */
public enum AccountType {
    ASSET(true),
    LIABILITY(false),
    EQUITY(false),
    REVENUE(false),
    EXPENSE(true);

    private final boolean debitNormal;

    AccountType(boolean debitNormal) {
        this.debitNormal = debitNormal;
    }

    /** 정상잔액이 차변이면 true. 시산표에서 잔액을 어느 쪽에 표시할지 결정한다. */
    public boolean isDebitNormal() {
        return debitNormal;
    }
}
