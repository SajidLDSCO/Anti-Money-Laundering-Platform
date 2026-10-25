package com.example.aml.api.dto;

public record RuleDto(String code, String name, String category, String description, String parameters,
                      int baseScore, boolean enabled) {
}
