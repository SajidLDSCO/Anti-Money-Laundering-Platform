package com.example.aml.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Self-registration. The role is always ANALYST; an admin can change it later. */
public record SignupRequest(
        // No "@" allowed, so a sign-in name is unambiguously either a username or an email.
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,40}",
                message = "use 3-40 letters, digits, dots, dashes or underscores") String username,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Email(message = "must be a valid email address") @Size(max = 254) String email,
        // BCrypt only uses the first 72 bytes.
        @NotBlank @Size(min = 10, max = 72, message = "must be 10-72 characters") String password) {
}
