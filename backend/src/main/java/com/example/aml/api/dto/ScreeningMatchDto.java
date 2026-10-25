package com.example.aml.api.dto;

public record ScreeningMatchDto(Long entryId, String listedName, String matchedOn, double score,
                                String entityType, String country, String listSource, String program) {
}
