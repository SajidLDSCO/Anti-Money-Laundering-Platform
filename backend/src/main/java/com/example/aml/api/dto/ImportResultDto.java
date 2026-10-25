package com.example.aml.api.dto;

import java.util.List;

public record ImportResultDto(int imported, int skipped, List<String> errors) {
}
