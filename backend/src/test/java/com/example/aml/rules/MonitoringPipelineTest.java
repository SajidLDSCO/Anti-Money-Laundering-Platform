package com.example.aml.rules;

import com.example.aml.api.dto.AccountRequest;
import com.example.aml.api.dto.AlertDetailDto;
import com.example.aml.api.dto.CustomerDto;
import com.example.aml.api.dto.CustomerRequest;
import com.example.aml.api.dto.EvaluationResultDto;
import com.example.aml.api.dto.SanctionsEntryDto;
import com.example.aml.api.dto.StatusChangeRequest;
import com.example.aml.api.dto.TransactionRequest;
import com.example.aml.domain.AlertStatus;
import com.example.aml.domain.CustomerType;
import com.example.aml.domain.KycStatus;
import com.example.aml.domain.RiskLevel;
import com.example.aml.domain.TransactionStatus;
import com.example.aml.domain.TransactionType;
import com.example.aml.screening.SanctionsScreeningService;
import com.example.aml.service.AlertService;
import com.example.aml.service.CustomerService;
import com.example.aml.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "aml.seed-demo-data=false")
class MonitoringPipelineTest {

    @Autowired CustomerService customers;
    @Autowired TransactionService transactions;
    @Autowired AlertService alerts;
    @Autowired SanctionsScreeningService sanctions;

    private long newAccount(String name, boolean pep) {
        CustomerDto c = customers.create(new CustomerRequest(name, CustomerType.INDIVIDUAL, null, "GB", "GB",
                "Engineer", null, pep, KycStatus.VERIFIED));
        return customers.openAccount(c.id(), new AccountRequest("CURRENT", "USD")).id();
    }

    private EvaluationResultDto submit(long account, TransactionType type, String amount, String counterparty,
                                       String country, Instant at) {
        return transactions.submit(new TransactionRequest(account, type, new BigDecimal(amount), null,
                counterparty, null, country, null, null, at));
    }

    @Test
    void ordinaryPaymentIsCleared() {
        long acc = newAccount("Plain Customer One", false);
        EvaluationResultDto r = submit(acc, TransactionType.CARD_PAYMENT, "42.50", "Corner Grocery", "GB", null);
        assertThat(r.decision()).isEqualTo("CLEARED");
        assertThat(r.alertId()).isNull();
    }

    @Test
    void structuringIsDetectedOnThirdDeposit() {
        long acc = newAccount("Structuring Tester", false);
        Instant t0 = Instant.now().minus(Duration.ofHours(30));
        assertThat(submit(acc, TransactionType.CASH_DEPOSIT, "9500", null, null, t0).hits())
                .noneMatch(h -> h.ruleCode().equals("STRUCTURING"));
        submit(acc, TransactionType.CASH_DEPOSIT, "9200", null, null, t0.plus(Duration.ofHours(8)));
        EvaluationResultDto third = submit(acc, TransactionType.CASH_DEPOSIT, "9700", null, null,
                t0.plus(Duration.ofHours(20)));

        assertThat(third.hits()).anyMatch(h -> h.ruleCode().equals("STRUCTURING"));
        assertThat(third.alertId()).isNotNull();
        assertThat(third.decision()).isIn("FLAGGED", "BLOCKED");
    }

    @Test
    void sanctionedCounterpartyIsBlockedAndReleasedOnFalsePositive() {
        sanctions.create(new SanctionsEntryDto(null, "Zephyr Arms Brokerage", null, "ENTITY", "XX", "TEST", null));
        long acc = newAccount("Sanctions Tester", false);
        submit(acc, TransactionType.TRANSFER_IN, "5000.50", "Employer", "GB", null);

        EvaluationResultDto r = submit(acc, TransactionType.WIRE_OUT, "1234.56", "Zephyr Arms Brokerage Ltd", "GB", null);
        assertThat(r.decision()).isEqualTo("BLOCKED");
        assertThat(r.score()).isEqualTo(100);
        assertThat(r.hits()).anyMatch(h -> h.ruleCode().equals(SanctionsMatchRule.CODE));

        assertThatThrownBy(() -> alerts.changeStatus(r.alertId(),
                new StatusChangeRequest(AlertStatus.CLOSED_FALSE_POSITIVE, "tester", " ")))
                .hasMessageContaining("rationale");

        AlertDetailDto closed = alerts.changeStatus(r.alertId(), new StatusChangeRequest(
                AlertStatus.CLOSED_FALSE_POSITIVE, "tester", "Different entity - verified registration documents."));
        assertThat(closed.transaction().status()).isEqualTo(TransactionStatus.FLAGGED);
        assertThat(closed.notes()).anyMatch(n -> n.text().contains("released"));
    }

    @Test
    void onboardingScreeningRaisesAlertAndHighRisk() {
        sanctions.create(new SanctionsEntryDto(null, "Borislav Kuznetsov", null, "INDIVIDUAL", "XX", "TEST", null));
        CustomerDto c = customers.create(new CustomerRequest("Borislav Kuznecov", CustomerType.INDIVIDUAL, null,
                "IR", "IR", "Arms dealer", null, true, KycStatus.PENDING));
        assertThat(c.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(customers.detail(c.id()).alerts()).hasSize(1);
    }

    @Test
    void highRiskJurisdictionIsScored() {
        long acc = newAccount("Jurisdiction Tester", false);
        EvaluationResultDto r = submit(acc, TransactionType.WIRE_OUT, "750", "Some Supplier", "KP", null);
        assertThat(r.hits()).anyMatch(h -> h.ruleCode().equals("HIGH_RISK_JURISDICTION"));
        assertThat(r.alertId()).isNotNull();
    }
}
