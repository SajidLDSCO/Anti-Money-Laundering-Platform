package com.example.aml.api.dto;

import com.example.aml.domain.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record AccountStatusRequest(@NotNull AccountStatus status) {
}
