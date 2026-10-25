package com.example.aml.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Username or email of an account whose confirmation email should be sent again. */
public record ResendVerificationRequest(@NotBlank @Size(max = 254) String usernameOrEmail) {
}
