package com.example.aml.api.dto;

import com.example.aml.domain.CustomerType;
import com.example.aml.domain.KycStatus;
import com.example.aml.domain.RiskLevel;

import java.time.Instant;
import java.time.LocalDate;

public record CustomerDto(Long id, String customerNumber, String fullName, CustomerType customerType,
                          LocalDate dateOfBirth, String nationality, String countryOfResidence,
                          String occupation, String email, boolean pep, KycStatus kycStatus,
                          int riskScore, RiskLevel riskLevel, String riskFactors, Instant createdAt) {
}
