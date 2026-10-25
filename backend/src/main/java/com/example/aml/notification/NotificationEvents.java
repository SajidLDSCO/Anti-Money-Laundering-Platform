package com.example.aml.notification;

import com.example.aml.domain.Severity;

/**
 * Things that happened which someone should hear about. Services publish them inside their
 * transaction; {@link EmailNotifier} sends the emails only after that transaction commits.
 */
public final class NotificationEvents {

    private NotificationEvents() {
    }

    /** Send the confirmation link to a new (or re-sent) sign-up. {@code token} is the raw one-time token. */
    public record VerificationEmailRequested(String username, String displayName, String email, String token) {}

    /** A self-registered account has confirmed its email and is waiting for an admin. */
    public record SignupAwaitingApproval(String username, String displayName, String email) {}

    /** An admin enabled an account. */
    public record AccountApproved(String username, String displayName, String email) {}

    /** An admin declined a sign-up; the account has been deleted. */
    public record AccountDeclined(String username, String displayName, String email) {}

    /** An analyst escalated an alert for the MLRO's filing decision. */
    public record AlertEscalated(Long alertId, String alertNumber, String customerName, String summary,
                                 String escalatedBy, String comment) {}

    /** A critical alert was raised, or monitoring held a transaction. */
    public record UrgentAlertRaised(Long alertId, String alertNumber, String customerName, String summary,
                                    Severity severity, boolean transactionHeld) {}
}
