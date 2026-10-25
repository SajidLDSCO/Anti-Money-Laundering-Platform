package com.example.aml.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code analyst} is set by the server from the signed-in user; any value sent by the client is ignored. */
public record SarRequest(String analyst,
                         @NotBlank @Size(min = 50, max = 8000, message = "narrative must be 50-8000 characters") String narrative) {
}
