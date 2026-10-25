package com.example.aml.api.dto;

import java.util.List;

public record AlertDetailDto(AlertSummaryDto alert, List<RuleHitDto> hits, TransactionDto transaction,
                             CustomerDto customer, List<NoteDto> notes, SarDto sar) {
}
