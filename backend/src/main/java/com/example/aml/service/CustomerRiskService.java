package com.example.aml.service;

import com.example.aml.config.AmlProperties;
import com.example.aml.domain.AlertStatus;
import com.example.aml.domain.Customer;
import com.example.aml.domain.CustomerType;
import com.example.aml.domain.KycStatus;
import com.example.aml.domain.RiskLevel;
import com.example.aml.repository.AlertRepository;
import com.example.aml.repository.SarReportRepository;
import com.example.aml.screening.SanctionsScreeningService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Customer risk rating (CRR). A transparent, additive scorecard: every point is
 * explained in {@code riskFactors} so the rating can be defended to an auditor.
 * Recalculated on onboarding, profile changes, new alerts and SAR filings.
 */
@Service
public class CustomerRiskService {

    public static final Set<AlertStatus> OPEN_STATUSES =
            EnumSet.of(AlertStatus.OPEN, AlertStatus.IN_REVIEW, AlertStatus.ESCALATED);

    private final AmlProperties props;
    private final AlertRepository alerts;
    private final SarReportRepository sars;
    private final SanctionsScreeningService screening;

    public CustomerRiskService(AmlProperties props, AlertRepository alerts, SarReportRepository sars,
                               SanctionsScreeningService screening) {
        this.props = props;
        this.alerts = alerts;
        this.sars = sars;
        this.screening = screening;
    }

    /** Updates the score, level and explanation on the (managed) customer entity. */
    public void recalculate(Customer c) {
        int score = 0;
        List<String> factors = new ArrayList<>();

        if (screening.bestHit(c.getFullName()) != null) {
            score += 40;
            factors.add("Possible sanctions list match (+40)");
        }
        if (c.isPep()) {
            score += 30;
            factors.add("Politically exposed person (+30)");
        }
        if (props.isHighRiskCountry(c.getCountryOfResidence()) || props.isHighRiskCountry(c.getNationality())) {
            score += 30;
            factors.add("Nationality/residence in high-risk jurisdiction (+30)");
        } else if (props.isIncreasedMonitoringCountry(c.getCountryOfResidence())
                || props.isIncreasedMonitoringCountry(c.getNationality())) {
            score += 15;
            factors.add("Nationality/residence under increased monitoring (+15)");
        }
        if (props.isHighRiskOccupation(c.getOccupation())) {
            score += 15;
            factors.add("High-risk occupation/industry: " + c.getOccupation() + " (+15)");
        }
        if (c.getCustomerType() == CustomerType.BUSINESS) {
            score += 10;
            factors.add("Legal entity - beneficial ownership risk (+10)");
        }
        if (c.getKycStatus() == KycStatus.PENDING) {
            score += 10;
            factors.add("KYC not yet verified (+10)");
        } else if (c.getKycStatus() == KycStatus.REJECTED) {
            score += 25;
            factors.add("KYC rejected (+25)");
        }
        if (c.getId() != null) {
            long open = alerts.countByCustomerIdAndStatusIn(c.getId(), OPEN_STATUSES);
            if (open > 0) {
                int pts = (int) Math.min(20, open * 5);
                score += pts;
                factors.add(open + " open alert(s) (+" + pts + ")");
            }
            long filed = sars.countByCustomerId(c.getId());
            if (filed > 0) {
                score += 25;
                factors.add(filed + " SAR(s) filed (+25)");
            }
        }

        score = Math.min(100, score);
        c.setRiskScore(score);
        c.setRiskLevel(RiskLevel.fromScore(score));
        c.setRiskFactors(factors.isEmpty() ? "No risk factors identified" : String.join("; ", factors));
    }
}
