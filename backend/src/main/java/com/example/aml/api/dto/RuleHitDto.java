package com.example.aml.api.dto;

public record RuleHitDto(String ruleCode, String ruleName, int score, String reason) {
}
