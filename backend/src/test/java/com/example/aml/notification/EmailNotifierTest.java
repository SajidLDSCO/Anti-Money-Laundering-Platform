package com.example.aml.notification;

import com.example.aml.api.dto.AccountRequest;
import com.example.aml.api.dto.CustomerDto;
import com.example.aml.api.dto.CustomerRequest;
import com.example.aml.api.dto.EvaluationResultDto;
import com.example.aml.api.dto.SanctionsEntryDto;
import com.example.aml.api.dto.SignupRequest;
import com.example.aml.api.dto.StatusChangeRequest;
import com.example.aml.api.dto.TransactionRequest;
import com.example.aml.api.dto.UserDto;
import com.example.aml.api.dto.UserUpdateRequest;
import com.example.aml.domain.AlertStatus;
import com.example.aml.domain.CustomerType;
import com.example.aml.domain.KycStatus;
import com.example.aml.domain.Role;
import com.example.aml.domain.TransactionType;
import com.example.aml.screening.SanctionsScreeningService;
import com.example.aml.service.AlertService;
import com.example.aml.service.CustomerService;
import com.example.aml.service.TransactionService;
import com.example.aml.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;

@SpringBootTest(properties = {"aml.seed-demo-data=false", "aml.security.signup-requires-approval=true",
        "aml.mail.app-url=https://aml.test/", "aml.mail.enabled=true"})
class EmailNotifierTest {

    private static final String PASSWORD = "correct-horse-battery";

    @MockitoBean JavaMailSender mail;
    @Autowired UserService users;
    @Autowired AlertService alerts;
    @Autowired CustomerService customers;
    @Autowired TransactionService transactions;
    @Autowired SanctionsScreeningService sanctions;

    @BeforeEach
    void staff() {
        users.createIfMissing("n.admin", "Notify Admin", "n.admin@test.example", PASSWORD, Role.ADMIN);
        users.createIfMissing("n.mlro", "Notify MLRO", "n.mlro@test.example", PASSWORD, Role.MLRO);
        users.createIfMissing("n.analyst", "Notify Analyst", "n.analyst@test.example", PASSWORD, Role.ANALYST);
    }

    @Test
    void signUpSendsAConfirmationLinkThenAdminsHearAndTheUserHearsWhenApproved() throws Exception {
        UserDto pending = users.signup(new SignupRequest("Pending.One", "Pending One", "pending.one@test.example",
                PASSWORD));

        SimpleMailMessage confirm = awaitMessageTo("pending.one@test.example");
        assertThat(confirm.getSubject()).isEqualTo("[AML Monitor] Confirm your email address");
        Matcher link = Pattern.compile("https://aml\\.test/verify-email\\?token=([A-Za-z0-9_-]+)")
                .matcher(confirm.getText());
        assertThat(link.find()).isTrue();
        // Admins only hear about the account once the address is confirmed.
        Thread.sleep(300);
        assertThat(sentTo("n.admin@test.example")).isEmpty();

        users.verifyEmail(link.group(1));
        SimpleMailMessage toAdmin = awaitMessageTo("n.admin@test.example");
        assertThat(toAdmin.getSubject()).isEqualTo("[AML Monitor] New account waiting for approval: pending.one");
        assertThat(toAdmin.getText()).contains("pending.one@test.example")
                .contains("https://aml.test/users?review=pending.one");
        assertThat(sentTo("n.analyst@test.example")).isEmpty();

        users.update(pending.id(), new UserUpdateRequest(null, true), "n.admin");
        SimpleMailMessage toUser = awaitMessage("pending.one@test.example", "[AML Monitor] Your AML Monitor account is ready");
        assertThat(toUser.getText()).contains("https://aml.test/login");
    }

    @Test
    void mlroHearsAboutEscalationsButAnalystsDoNot() throws Exception {
        long account = newAccount("Escalation Notify Tester");
        // 15,000 cash: large cash (30) + round amount (10) = 40, flagged but neither held nor critical.
        EvaluationResultDto r = transactions.submit(new TransactionRequest(account, TransactionType.CASH_DEPOSIT,
                new BigDecimal("15000"), null, null, null, null, null, null, null));
        assertThat(r.alertId()).isNotNull();

        alerts.changeStatus(r.alertId(), new StatusChangeRequest(AlertStatus.ESCALATED, "n.analyst",
                "Source of funds unclear."));

        SimpleMailMessage toMlro = awaitMessageTo("n.mlro@test.example");
        assertThat(toMlro.getSubject()).startsWith("[AML Monitor] Alert ").endsWith(" escalated to the MLRO");
        assertThat(toMlro.getText()).contains("n.analyst escalated").contains("Source of funds unclear.")
                .contains("https://aml.test/alerts/" + r.alertId());
        assertThat(awaitMessageTo("n.admin@test.example").getSubject()).isEqualTo(toMlro.getSubject());
        assertThat(sentTo("n.analyst@test.example")).isEmpty();
    }

    @Test
    void heldTransactionsAreReportedToTheMlro() throws Exception {
        sanctions.create(new SanctionsEntryDto(null, "Quillon Arms Syndicate", null, "ENTITY", "XX", "TEST", null));
        long account = newAccount("Held Notify Tester");
        EvaluationResultDto r = transactions.submit(new TransactionRequest(account, TransactionType.WIRE_OUT,
                new BigDecimal("1200"), null, "Quillon Arms Syndicate", null, "XX", null, null, null));
        assertThat(r.decision()).isEqualTo("BLOCKED");

        SimpleMailMessage toMlro = awaitMessageTo("n.mlro@test.example");
        assertThat(toMlro.getSubject()).startsWith("[AML Monitor] Transaction held: ")
                .endsWith("Held Notify Tester");
        assertThat(toMlro.getText()).contains("held the transaction");
    }

    private long newAccount(String name) {
        CustomerDto c = customers.create(new CustomerRequest(name, CustomerType.INDIVIDUAL, null, "GB", "GB",
                "Engineer", null, false, KycStatus.VERIFIED));
        return customers.openAccount(c.id(), new AccountRequest("CURRENT", "USD")).id();
    }

    /** Emails are sent on a background thread after commit, so wait for them. */
    private SimpleMailMessage awaitMessageTo(String address) throws InterruptedException {
        return awaitMessage(address, null);
    }

    private SimpleMailMessage awaitMessage(String address, String subject) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            List<SimpleMailMessage> found = sentTo(address).stream()
                    .filter(m -> subject == null || subject.equals(m.getSubject())).toList();
            if (!found.isEmpty()) {
                return found.get(0);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No email" + (subject == null ? "" : " \"" + subject + "\"") + " sent to " + address);
    }

    private List<SimpleMailMessage> sentTo(String address) {
        return mockingDetails(mail).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("send"))
                .map(Invocation::getArguments)
                .flatMap(Arrays::stream)
                .filter(SimpleMailMessage.class::isInstance)
                .map(SimpleMailMessage.class::cast)
                .filter(m -> m.getTo() != null && Arrays.asList(m.getTo()).contains(address))
                .toList();
    }
}
