package com.example.aml.api.dto;

import java.time.Instant;

public record SarDto(Long id, String sarNumber, Long alertId, String alertNumber, Long customerId,
                     String customerName, String narrative, String filedBy, Instant filedAt) {
}
