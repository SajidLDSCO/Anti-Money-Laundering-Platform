package com.example.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A person who can sign in. The username is what appears on assignments, notes and SARs. */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored lower-case; sign-in is case-insensitive. */
    @Column(nullable = false, unique = true, length = 40)
    private String username;

    @Column(nullable = false, length = 100)
    private String displayName;

    /** Stored lower-case; can be used instead of the username to sign in. Null for accounts created before email was required. */
    @Column(unique = true, length = 254)
    private String email;

    /** Delegating encoder format, e.g. {bcrypt}$2a$10$... */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.ANALYST;

    /** False until an administrator approves a self-registered account. */
    private boolean enabled;

    /**
     * False from sign-up until the person clicks the link in the confirmation email. Accounts created
     * any other way (admin bootstrap, demo users, rows from before this column existed) count as confirmed.
     */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean emailVerified = true;

    /** SHA-256 (hex) of the outstanding email confirmation token; the token itself is only in the email. */
    @Column(length = 64, unique = true)
    private String verificationTokenHash;

    private Instant verificationExpiresAt;

    private Instant verificationSentAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant lastLoginAt;

    protected AppUser() {
    }

    public AppUser(String username, String displayName, String email, String passwordHash, Role role,
                   boolean enabled) {
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public String getVerificationTokenHash() { return verificationTokenHash; }
    public Instant getVerificationExpiresAt() { return verificationExpiresAt; }
    public Instant getVerificationSentAt() { return verificationSentAt; }

    /** Starts (or restarts) email confirmation with a new token. */
    public void requireVerification(String tokenHash, Instant expiresAt) {
        this.emailVerified = false;
        this.verificationTokenHash = tokenHash;
        this.verificationExpiresAt = expiresAt;
        this.verificationSentAt = Instant.now();
    }

    public void markEmailVerified() {
        this.emailVerified = true;
        this.verificationTokenHash = null;
        this.verificationExpiresAt = null;
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
