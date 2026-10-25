package com.example.aml.api.dto;

import java.util.List;

/** Returned when a transaction is submitted: what the engine decided and why. */
public record EvaluationResultDto(TransactionDto transaction, int score, String decision,
                                  List<RuleHitDto> hits, Long alertId, String alertNumber) {
}
