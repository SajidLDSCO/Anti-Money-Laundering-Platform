package com.example.aml.domain;

public enum Severity {
    LOW, MEDIUM, HIGH, CRITICAL;

    public static Severity fromScore(int score) {
        if (score >= 80) return CRITICAL;
        if (score >= 60) return HIGH;
        if (score >= 40) return MEDIUM;
        return LOW;
    }
}
