package com.example.aml.api.dto;

import com.example.aml.domain.TransactionStatus;
import com.example.aml.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionDto(Long id, String reference, Long accountId, String accountNumber, Long customerId,
                             String customerName, TransactionType type, BigDecimal amount, String currency,
                             String counterpartyName, String counterpartyAccount, String counterpartyCountry,
                             String channel, String description, Instant timestamp, int riskScore,
                             TransactionStatus status) {
}
