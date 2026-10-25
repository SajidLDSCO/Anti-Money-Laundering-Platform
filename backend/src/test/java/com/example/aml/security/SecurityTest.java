package com.example.aml.security;

import com.example.aml.api.dto.AccountRequest;
import com.example.aml.api.dto.CustomerDto;
import com.example.aml.api.dto.CustomerRequest;
import com.example.aml.api.dto.EvaluationResultDto;
import com.example.aml.api.dto.TransactionRequest;
import com.example.aml.domain.CustomerType;
import com.example.aml.domain.KycStatus;
import com.example.aml.domain.Role;
import com.example.aml.domain.TransactionType;
import com.example.aml.notification.NotificationEvents.AccountDeclined;
import com.example.aml.notification.NotificationEvents.SignupAwaitingApproval;
import com.example.aml.notification.NotificationEvents.VerificationEmailRequested;
import com.example.aml.service.CustomerService;
import com.example.aml.service.TransactionService;
import com.example.aml.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"aml.seed-demo-data=false", "aml.security.signup-requires-approval=true"})
@AutoConfigureMockMvc
@RecordApplicationEvents
class SecurityTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired MockMvc mvc;
    @Autowired ApplicationEvents events;
    @Autowired UserService users;
    @Autowired CustomerService customers;
    @Autowired TransactionService transactions;

    @BeforeEach
    void users() {
        users.createIfMissing("t.analyst", "Test Analyst", "t.analyst@test.example", PASSWORD, Role.ANALYST);
        users.createIfMissing("t.admin", "Test Admin", "t.admin@test.example", PASSWORD, Role.ADMIN);
    }

    @Test
    void apiRequiresSignIn() throws Exception {
        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Sign in to continue."));
    }

    @Test
    void signInStartsASessionAndSignOutEndsIt() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(formLogin("/api/auth/login")
                        .user("T.Analyst").password(PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("t.analyst"))
                .andExpect(jsonPath("$.role").value("ANALYST"))
                .andReturn().getRequest().getSession(false);

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Test Analyst"));

        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(formLogin("/api/auth/login").user("t.analyst").password("not-the-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Incorrect username, email or password."));
    }

    @Test
    void signUpNeedsEmailConfirmationThenApprovalWithoutRevealingAccountsToGuessers() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"New.Person","displayName":"New Person","email":"New.Person@Test.Example","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new.person"))
                .andExpect(jsonPath("$.email").value("new.person@test.example"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.role").value("ANALYST"))
                .andExpect(jsonPath("$.enabled").value(false));
        String token = events.stream(VerificationEmailRequested.class)
                .filter(e -> e.username().equals("new.person")).findFirst().orElseThrow().token();

        // Before confirming: the right password says "confirm your email", a wrong one says nothing more.
        mvc.perform(formLogin("/api/auth/login").user("new.person").password("wrong-password-123"))
                .andExpect(jsonPath("$.detail").value("Incorrect username, email or password."));
        mvc.perform(formLogin("/api/auth/login").user("new.person").password(PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        mvc.perform(post("/api/auth/verify-email").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
        assertThat(events.stream(SignupAwaitingApproval.class)).anyMatch(e -> e.username().equals("new.person"));
        // The link works once.
        mvc.perform(post("/api/auth/verify-email").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isConflict());

        mvc.perform(formLogin("/api/auth/login").user("new.person").password(PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("APPROVAL_PENDING"))
                .andExpect(jsonPath("$.detail").value("Your account is waiting for an administrator to approve it."));

        long id = users.list().stream().filter(u -> u.username().equals("new.person")).findFirst().orElseThrow().id();
        mvc.perform(patch("/api/users/" + id).with(user("t.admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        mvc.perform(formLogin("/api/auth/login").user("new.person@test.example").password(PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void adminsCanDeclineSignUpsButNotAccountsThatWereEverActive() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"to.decline","displayName":"To Decline","email":"to.decline@test.example","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isCreated());
        long pendingId = idOf("to.decline");
        long activeId = idOf("t.analyst");

        mvc.perform(delete("/api/users/" + pendingId).with(user("t.analyst").roles("ANALYST")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/" + activeId).with(user("t.admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/users/" + pendingId).with(user("t.admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(users.list()).noneMatch(u -> u.username().equals("to.decline"));
        assertThat(events.stream(AccountDeclined.class)).anyMatch(e -> e.email().equals("to.decline@test.example"));
    }

    private long idOf(String username) {
        return users.list().stream().filter(u -> u.username().equals(username)).findFirst().orElseThrow().id();
    }

    @Test
    void badConfirmationLinksAndResendsGiveNothingAway() throws Exception {
        mvc.perform(post("/api/auth/verify-email").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"made-up-token\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/resend-verification").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"nobody@test.example\"}"))
                .andExpect(status().isAccepted());
        // Already-confirmed accounts get no new link.
        mvc.perform(post("/api/auth/resend-verification").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"t.analyst\"}"))
                .andExpect(status().isAccepted());
        assertThat(events.stream(VerificationEmailRequested.class)).isEmpty();
    }

    @Test
    void duplicateAndWeakSignUpsAreRefused() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"t.analyst","displayName":"Copy","email":"copy@test.example","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"short.pw","displayName":"Short","email":"short@test.example","password":"short"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emailMustBeValidAndUnused() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"other.name","displayName":"Copy","email":"T.Analyst@test.example","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with this email already exists. Sign in instead."));
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"no.email","displayName":"No Email","email":"not-an-email","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"no.email","displayName":"No Email","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signInWorksWithEmailAndTheSessionIsTheUsername() throws Exception {
        mvc.perform(formLogin("/api/auth/login").user("T.Analyst@Test.Example").password(PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("t.analyst"))
                .andExpect(jsonPath("$.email").value("t.analyst@test.example"));
        mvc.perform(formLogin("/api/auth/login").user("t.analyst@test.example").password("not-the-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Incorrect username, email or password."));
        mvc.perform(formLogin("/api/auth/login").user("nobody@test.example").password(PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Incorrect username, email or password."));
    }

    @Test
    void mutatingRequestsNeedTheCsrfToken() throws Exception {
        mvc.perform(post("/api/customers/1/recalculate-risk").with(user("t.analyst").roles("ANALYST")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolesLimitSensitiveActions() throws Exception {
        String toggle = "{\"enabled\":true}";
        mvc.perform(patch("/api/rules/LARGE_CASH").with(user("t.analyst").roles("ANALYST")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(toggle))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Your role does not allow this action."));
        mvc.perform(patch("/api/rules/LARGE_CASH").with(user("t.admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(toggle))
                .andExpect(status().isOk());

        mvc.perform(post("/api/alerts/1/sar").with(user("t.analyst").roles("ANALYST")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"narrative\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users").with(user("t.analyst").roles("ANALYST"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/users").with(user("t.admin").roles("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void notesAreSignedByTheSessionUserNotTheRequestBody() throws Exception {
        CustomerDto c = customers.create(new CustomerRequest("Note Author Tester", CustomerType.INDIVIDUAL, null,
                "GB", "GB", "Engineer", null, false, KycStatus.VERIFIED));
        long account = customers.openAccount(c.id(), new AccountRequest("CURRENT", "USD")).id();
        // 15,000 cash: large cash (30) + round amount (10) reaches the alert threshold of 40.
        EvaluationResultDto r = transactions.submit(new TransactionRequest(account, TransactionType.CASH_DEPOSIT,
                new BigDecimal("15000"), null, null, null, null, null, null, null));
        assertThat(r.alertId()).isNotNull();

        mvc.perform(post("/api/alerts/" + r.alertId() + "/notes").with(user("t.analyst").roles("ANALYST"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"someone.else\",\"text\":\"Called the customer.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("t.analyst"));
    }

    @Test
    void adminsCannotLockThemselvesOut() throws Exception {
        long adminId = users.list().stream().filter(u -> u.username().equals("t.admin")).findFirst().orElseThrow().id();
        mvc.perform(patch("/api/users/" + adminId).with(user("t.admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isConflict());
    }
}
