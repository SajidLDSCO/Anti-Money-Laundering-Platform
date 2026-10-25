package com.example.aml.service;

import com.example.aml.api.DtoMapper;
import com.example.aml.api.dto.DashboardDto;
import com.example.aml.domain.RiskLevel;
import com.example.aml.domain.Severity;
import com.example.aml.domain.TransactionStatus;
import com.example.aml.repository.AlertRepository;
import com.example.aml.repository.CustomerRepository;
import com.example.aml.repository.SarReportRepository;
import com.example.aml.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class DashboardService {

    private static final int DAYS = 30;

    private final TransactionRepository transactions;
    private final AlertRepository alerts;
    private final CustomerRepository customers;
    private final SarReportRepository sars;

    public DashboardService(TransactionRepository transactions, AlertRepository alerts,
                            CustomerRepository customers, SarReportRepository sars) {
        this.transactions = transactions;
        this.alerts = alerts;
        this.customers = customers;
        this.sars = sars;
    }

    @Transactional(readOnly = true)
    public DashboardDto summary() {
        var open = CustomerRiskService.OPEN_STATUSES;

        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (Severity s : Severity.values()) {
            bySeverity.put(s.name(), 0L);
        }
        for (Object[] row : alerts.countBySeverity(open)) {
            bySeverity.put(((Severity) row[0]).name(), (Long) row[1]);
        }

        Map<String, Long> byRule = new TreeMap<>();
        for (Object[] row : alerts.countByRule()) {
            byRule.put((String) row[0], (Long) row[1]);
        }

        Map<String, Long> byRisk = new LinkedHashMap<>();
        for (RiskLevel l : RiskLevel.values()) {
            byRisk.put(l.name(), 0L);
        }
        for (Object[] row : customers.countGroupedByRiskLevel()) {
            byRisk.put(((RiskLevel) row[0]).name(), (Long) row[1]);
        }

        Instant from = LocalDate.now(ZoneOffset.UTC).minusDays(DAYS - 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new DashboardDto(
                transactions.count(),
                transactions.countByStatus(TransactionStatus.FLAGGED),
                transactions.countByStatus(TransactionStatus.BLOCKED),
                transactions.sumAmountSince(Instant.now().minus(Duration.ofDays(30))),
                alerts.countByStatusIn(open),
                alerts.countByStatusInAndSeverity(open, Severity.CRITICAL),
                customers.countByRiskLevel(RiskLevel.HIGH),
                sars.count(),
                bySeverity, byRule, byRisk,
                daily(from),
                alerts.findRecent().stream().map(DtoMapper::toSummary).toList());
    }

    private List<DashboardDto.DailyPoint> daily(Instant from) {
        LocalDate start = from.atZone(ZoneOffset.UTC).toLocalDate();
        long[][] counts = new long[DAYS][3];
        for (Object[] row : transactions.timelineSince(from)) {
            LocalDate d = ((Instant) row[0]).atZone(ZoneOffset.UTC).toLocalDate();
            int idx = (int) (d.toEpochDay() - start.toEpochDay());
            if (idx < 0 || idx >= DAYS) {
                continue;
            }
            counts[idx][0]++;
            TransactionStatus s = (TransactionStatus) row[1];
            if (s == TransactionStatus.FLAGGED) {
                counts[idx][1]++;
            } else if (s == TransactionStatus.BLOCKED) {
                counts[idx][2]++;
            }
        }
        List<DashboardDto.DailyPoint> points = new ArrayList<>(DAYS);
        for (int i = 0; i < DAYS; i++) {
            points.add(new DashboardDto.DailyPoint(start.plusDays(i), counts[i][0], counts[i][1], counts[i][2]));
        }
        return points;
    }
}
