package com.example.aml.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SanctionsEntryDto(Long id,
                                @NotBlank @Size(max = 255) String name,
                                @Size(max = 2000) String aliases,
                                @Size(max = 16) String entityType,
                                @Size(max = 2) String country,
                                @Size(max = 32) String listSource,
                                @Size(max = 64) String program) {
}
