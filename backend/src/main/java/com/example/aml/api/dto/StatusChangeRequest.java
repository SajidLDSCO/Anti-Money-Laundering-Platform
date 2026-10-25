package com.example.aml.api.dto;

import com.example.aml.domain.AlertStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code analyst} is set by the server from the signed-in user; any value sent by the client is ignored. */
public record StatusChangeRequest(@NotNull AlertStatus status, String analyst, @Size(max = 4000) String comment) {
}
