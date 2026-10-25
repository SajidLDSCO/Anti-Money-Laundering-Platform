package com.example.aml.api.dto;

import com.example.aml.domain.AlertStatus;
import com.example.aml.domain.AlertType;
import com.example.aml.domain.Severity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AlertSummaryDto(Long id, String alertNumber, AlertType type, Long customerId, String customerName,
                              Long transactionId, String transactionReference, BigDecimal amount, String currency,
                              int score, Severity severity, AlertStatus status, String assignedTo, String summary,
                              List<String> ruleCodes, Instant createdAt, Instant updatedAt) {
}
