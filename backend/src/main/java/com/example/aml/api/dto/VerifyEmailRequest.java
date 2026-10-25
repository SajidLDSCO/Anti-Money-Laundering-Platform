package com.example.aml.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The token from the link in the confirmation email. */
public record VerifyEmailRequest(@NotBlank @Size(max = 100) String token) {
}
