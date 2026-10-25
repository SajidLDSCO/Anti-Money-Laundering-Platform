package com.example.aml.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DashboardDto(long totalTransactions, long flaggedTransactions, long blockedTransactions,
                           BigDecimal volumeLast30Days, long openAlerts, long criticalOpenAlerts,
                           long highRiskCustomers, long sarsFiled,
                           Map<String, Long> openAlertsBySeverity, Map<String, Long> alertsByRule,
                           Map<String, Long> customersByRisk, List<DailyPoint> daily,
                           List<AlertSummaryDto> recentAlerts) {

    public record DailyPoint(LocalDate date, long total, long flagged, long blocked) {
    }
}
