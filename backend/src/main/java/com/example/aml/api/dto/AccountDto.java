package com.example.aml.api.dto;

import com.example.aml.domain.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountDto(Long id, String accountNumber, Long customerId, String customerName, String accountType,
                         String currency, BigDecimal balance, AccountStatus status, Instant openedAt) {
}
