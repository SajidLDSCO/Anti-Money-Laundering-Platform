package com.example.aml.domain;

public enum TransactionType {
    CASH_DEPOSIT(true, true),
    CASH_WITHDRAWAL(true, false),
    WIRE_IN(false, true),
    WIRE_OUT(false, false),
    TRANSFER_IN(false, true),
    TRANSFER_OUT(false, false),
    CARD_PAYMENT(false, false);

    private final boolean cash;
    private final boolean inbound;

    TransactionType(boolean cash, boolean inbound) {
        this.cash = cash;
        this.inbound = inbound;
    }

    public boolean isCash() { return cash; }

    public boolean isInbound() { return inbound; }
}
