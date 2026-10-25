package com.example.aml.notification;

import com.example.aml.config.AmlProperties;
import com.example.aml.domain.AppUser;
import com.example.aml.domain.Role;
import com.example.aml.notification.NotificationEvents.AccountApproved;
import com.example.aml.notification.NotificationEvents.AccountDeclined;
import com.example.aml.notification.NotificationEvents.AlertEscalated;
import com.example.aml.notification.NotificationEvents.SignupAwaitingApproval;
import com.example.aml.notification.NotificationEvents.UrgentAlertRaised;
import com.example.aml.notification.NotificationEvents.VerificationEmailRequested;
import com.example.aml.repository.AppUserRepository;
import com.example.aml.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Sends notification emails over SMTP.
 * <ul>
 *   <li>Runs after the publishing transaction commits, on a background thread, so a slow or
 *       failing mail server never delays or rolls back the action that caused it.</li>
 *   <li>Silent until the application is ready, so seeding demo data sends nothing.</li>
 *   <li>Without {@code spring.mail.host} there is no mail sender and messages are only logged.</li>
 *   <li>One message per recipient, so recipients do not see each other's addresses.</li>
 * </ul>
 */
@Component
public class EmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);
    private static final Set<Role> MLRO_AND_ADMINS = Set.of(Role.MLRO, Role.ADMIN);

    private final ObjectProvider<JavaMailSender> sender;
    private final AppUserRepository users;
    private final AmlProperties props;
    private volatile boolean ready;

    public EmailNotifier(ObjectProvider<JavaMailSender> sender, AppUserRepository users, AmlProperties props) {
        this.sender = sender;
        this.users = users;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    void applicationReady() {
        ready = true;
        JavaMailSender mail = activeSender();
        log.info(mail == null
                ? "Email notifications are off: " + (enabled() ? "set SPRING_MAIL_HOST to send them." : "aml.mail.enabled=false.")
                : "Email notifications are on, via " + describe(mail) + ".");
    }

    /** The mail sender, or null when no SMTP host is configured or mail is switched off. */
    private JavaMailSender activeSender() {
        return enabled() ? sender.getIfAvailable() : null;
    }

    private boolean enabled() {
        return props.mail() == null || props.mail().enabled();
    }

    private static String describe(JavaMailSender mail) {
        return mail instanceof JavaMailSenderImpl impl ? impl.getHost() + ":" + impl.getPort() : "SMTP";
    }

    @Async
    @TransactionalEventListener
    public void on(VerificationEmailRequested e) {
        String link = appUrl() + "/verify-email?token=" + e.token();
        if (activeSender() == null) {
            // Local development without SMTP: the link is the only way to finish signing up.
            log.warn("Mail is off, so the confirmation email for {} was not sent. Confirmation link: {}",
                    e.username(), link);
            return;
        }
        String body = """
                Hello %s,

                Confirm your email address to finish creating your AML Monitor account (%s):
                %s

                The link works once and expires in %d hours. After you confirm, an administrator reviews
                your account and you get another email when you can sign in.

                If you did not sign up, ignore this email.
                """.formatted(e.displayName(), e.username(), link, UserService.VERIFICATION_VALIDITY.toHours());
        sendTo(List.of(e.email()), "Confirm your email address", body);
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(SignupAwaitingApproval e) {
        String body = """
                %s (%s, %s) has confirmed their email address and is waiting for approval.

                Review it (approve or decline):
                %s/users?review=%s
                """.formatted(e.displayName(), e.username(), e.email(), appUrl(),
                URLEncoder.encode(e.username(), StandardCharsets.UTF_8));
        sendTo(recipients(Set.of(Role.ADMIN)), "New account waiting for approval: " + e.username(), body);
    }

    @Async
    @TransactionalEventListener
    public void on(AccountApproved e) {
        if (e.email() == null) {
            return;
        }
        String body = """
                Hello %s,

                Your AML Monitor account (%s) has been approved. You can sign in with your username or this email address:
                %s/login
                """.formatted(e.displayName(), e.username(), appUrl());
        sendTo(List.of(e.email()), "Your AML Monitor account is ready", body);
    }

    @Async
    @TransactionalEventListener
    public void on(AccountDeclined e) {
        if (e.email() == null) {
            return;
        }
        String body = """
                Hello %s,

                Your request for an AML Monitor account (%s) was not approved, and the account has been removed.
                If you think this is a mistake, contact your administrator.
                """.formatted(e.displayName(), e.username());
        sendTo(List.of(e.email()), "Your AML Monitor account request", body);
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(AlertEscalated e) {
        String body = """
                %s escalated alert %s for a filing decision.

                Customer: %s
                Finding: %s
                %s
                Open the alert:
                %s/alerts/%d
                """.formatted(e.escalatedBy(), e.alertNumber(), e.customerName(), e.summary(),
                e.comment() == null || e.comment().isBlank() ? "" : "Comment: " + e.comment().trim() + "\n",
                appUrl(), e.alertId());
        sendTo(recipients(MLRO_AND_ADMINS), "Alert " + e.alertNumber() + " escalated to the MLRO", body);
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(UrgentAlertRaised e) {
        String what = e.transactionHeld() ? "Transaction held" : "Critical alert";
        String body = """
                Monitoring raised %s alert %s%s.

                Customer: %s
                Finding: %s

                Open the alert:
                %s/alerts/%d
                """.formatted(e.severity().name().toLowerCase(), e.alertNumber(),
                e.transactionHeld() ? " and held the transaction until an analyst decides" : "",
                e.customerName(), e.summary(), appUrl(), e.alertId());
        sendTo(recipients(MLRO_AND_ADMINS), what + ": " + e.alertNumber() + " – " + e.customerName(), body);
    }

    private List<String> recipients(Set<Role> roles) {
        return users.findByRoleInAndEnabledTrueAndEmailIsNotNull(roles).stream().map(AppUser::getEmail).toList();
    }

    private void sendTo(List<String> to, String subject, String body) {
        if (!ready || to.isEmpty()) {
            return;
        }
        // Subjects carry user-supplied names: keep them on one line.
        String cleanSubject = "[AML Monitor] " + subject.replaceAll("[\\r\\n]+", " ");
        JavaMailSender mail = activeSender();
        if (mail == null) {
            log.info("Email not sent (mail is off) to {} recipient(s): {}", to.size(), cleanSubject);
            return;
        }
        for (String address : to) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from());
            message.setTo(address);
            message.setSubject(cleanSubject);
            message.setText(body + "\n--\nAML Monitor. You receive this because of your role in the system.\n");
            try {
                mail.send(message);
            } catch (MailException ex) {
                log.warn("Could not send \"{}\" to {}: {}", cleanSubject, address, ex.getMessage());
            }
        }
    }

    private String from() {
        return props.mail() == null || props.mail().from() == null ? "AML Monitor <no-reply@aml.local>" : props.mail().from();
    }

    private String appUrl() {
        String url = props.mail() == null || props.mail().appUrl() == null ? "http://localhost:4200" : props.mail().appUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
