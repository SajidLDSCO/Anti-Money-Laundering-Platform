package com.example.aml.api.dto;

import java.util.List;

public record ScreeningResultDto(String query, double threshold, boolean hit, List<ScreeningMatchDto> matches) {
}
