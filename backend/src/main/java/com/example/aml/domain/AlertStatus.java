package com.example.aml.domain;

public enum AlertStatus {
    OPEN, IN_REVIEW, ESCALATED, CLOSED_FALSE_POSITIVE, CLOSED_SAR_FILED;

    public boolean isClosed() {
        return this == CLOSED_FALSE_POSITIVE || this == CLOSED_SAR_FILED;
    }
}
