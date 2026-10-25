package com.example.aml.api.dto;

import com.example.aml.domain.CustomerType;
import com.example.aml.domain.KycStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CustomerRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotNull CustomerType customerType,
        LocalDate dateOfBirth,
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be an ISO 3166 alpha-2 code") String nationality,
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be an ISO 3166 alpha-2 code") String countryOfResidence,
        @Size(max = 200) String occupation,
        @Email String email,
        boolean pep,
        KycStatus kycStatus) {
}
