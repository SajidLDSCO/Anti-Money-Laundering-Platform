package com.example.aml.api.dto;

import java.util.List;

public record CustomerDetailDto(CustomerDto customer, List<AccountDto> accounts,
                                List<TransactionDto> recentTransactions, List<AlertSummaryDto> alerts) {
}
