package com.example.aml.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AccountRequest(
        @NotBlank String accountType,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 code") String currency) {
}
