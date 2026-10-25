package com.example.aml.api.dto;

import com.example.aml.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A transaction submitted for monitoring. {@code timestamp} is optional and defaults to now;
 * it can be set for back-dated batch loads from a core banking system.
 */
public record TransactionRequest(
        @NotNull Long accountId,
        @NotNull TransactionType type,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 code") String currency,
        @Size(max = 200) String counterpartyName,
        @Size(max = 34) String counterpartyAccount,
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be an ISO 3166 alpha-2 code") String counterpartyCountry,
        @Size(max = 24) String channel,
        @Size(max = 500) String description,
        Instant timestamp) {
}
