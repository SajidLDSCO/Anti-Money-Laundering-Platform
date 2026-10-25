package com.example.aml.domain;

public enum RiskLevel {
    LOW, MEDIUM, HIGH;

    public static RiskLevel fromScore(int score) {
        if (score >= 60) return HIGH;
        if (score >= 30) return MEDIUM;
        return LOW;
    }
}
