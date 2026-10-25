package com.example.aml.service;

import com.example.aml.api.dto.SignupRequest;
import com.example.aml.api.dto.UserDto;
import com.example.aml.api.dto.UserUpdateRequest;
import com.example.aml.common.BusinessRuleException;
import com.example.aml.common.NotFoundException;
import com.example.aml.config.AmlProperties;
import com.example.aml.domain.AppUser;
import com.example.aml.domain.Role;
import com.example.aml.notification.NotificationEvents.AccountApproved;
import com.example.aml.notification.NotificationEvents.AccountDeclined;
import com.example.aml.notification.NotificationEvents.SignupAwaitingApproval;
import com.example.aml.notification.NotificationEvents.VerificationEmailRequested;
import com.example.aml.repository.AppUserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class UserService {

    public static final Duration VERIFICATION_VALIDITY = Duration.ofHours(24);
    private static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AmlProperties props;
    private final ApplicationEventPublisher events;

    public UserService(AppUserRepository users, PasswordEncoder encoder, AmlProperties props,
                       ApplicationEventPublisher events) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
        this.events = events;
    }

    public static String normalise(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UserDto signup(SignupRequest req) {
        String username = normalise(req.username());
        String email = normalise(req.email());
        if (users.existsByUsername(username)) {
            throw new BusinessRuleException("The username " + username + " is already taken.");
        }
        if (users.existsByEmail(email)) {
            throw new BusinessRuleException("An account with this email already exists. Sign in instead.");
        }
        AppUser user = new AppUser(username, req.displayName().trim(), email, encoder.encode(req.password()),
                Role.ANALYST, !approvalRequired());
        sendVerification(user);
        users.save(user);
        return toDto(user);
    }

    /**
     * Confirms an email from the link in the confirmation email. Admins hear about the account only
     * now, so they never review sign-ups with an address nobody controls.
     */
    @Transactional
    public UserDto verifyEmail(String token) {
        AppUser user = users.findByVerificationTokenHash(hash(token))
                .filter(u -> u.getVerificationExpiresAt() != null && u.getVerificationExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessRuleException(
                        "This confirmation link is invalid or has expired. Request a new one from the sign-in page."));
        user.markEmailVerified();
        if (!user.isEnabled()) {
            events.publishEvent(new SignupAwaitingApproval(user.getUsername(), user.getDisplayName(), user.getEmail()));
        }
        return toDto(user);
    }

    /**
     * Sends a new confirmation link if the account exists and is unconfirmed. Says nothing either way,
     * so it cannot be used to find out which accounts exist, and sends at most one email a minute.
     */
    @Transactional
    public void resendVerification(String usernameOrEmail) {
        findForSignIn(usernameOrEmail)
                .filter(u -> !u.isEmailVerified() && u.getEmail() != null)
                .filter(u -> u.getVerificationSentAt() == null
                        || u.getVerificationSentAt().isBefore(Instant.now().minus(RESEND_INTERVAL)))
                .ifPresent(this::sendVerification);
    }

    private void sendVerification(AppUser user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.requireVerification(hash(token), Instant.now().plus(VERIFICATION_VALIDITY));
        events.publishEvent(new VerificationEmailRequested(user.getUsername(), user.getDisplayName(),
                user.getEmail(), token));
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private boolean approvalRequired() {
        return props.security() != null && props.security().signupRequiresApproval();
    }

    /** Finds the account for a sign-in name, which is an email if it contains "@" and a username otherwise. */
    @Transactional(readOnly = true)
    public Optional<AppUser> findForSignIn(String usernameOrEmail) {
        String key = normalise(usernameOrEmail);
        return key.contains("@") ? users.findByEmail(key) : users.findByUsername(key);
    }

    @Transactional(readOnly = true)
    public UserDto current(String username) {
        return toDto(users.findByUsername(username).orElseThrow(() -> new NotFoundException("User", username)));
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return users.findAllByOrderByEnabledAscCreatedAtDesc().stream().map(UserService::toDto).toList();
    }

    /** Admins cannot change their own role or disable themselves, so there is always one admin left. */
    @Transactional
    public UserDto update(Long id, UserUpdateRequest req, String actingUsername) {
        AppUser user = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        boolean self = user.getUsername().equals(actingUsername);
        if (req.role() != null && req.role() != user.getRole()) {
            if (self) {
                throw new BusinessRuleException("You cannot change your own role.");
            }
            user.setRole(req.role());
        }
        if (req.enabled() != null && req.enabled() != user.isEnabled()) {
            if (self) {
                throw new BusinessRuleException("You cannot disable your own account.");
            }
            user.setEnabled(req.enabled());
            if (user.isEnabled()) {
                events.publishEvent(new AccountApproved(user.getUsername(), user.getDisplayName(), user.getEmail()));
            }
        }
        return toDto(user);
    }

    /**
     * Declines a sign-up by deleting the account. Only for accounts that were never approved: anyone who
     * has signed in may appear in case trails and SARs, so those are disabled instead, never deleted.
     */
    @Transactional
    public void decline(Long id) {
        AppUser user = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        if (user.isEnabled() || user.getLastLoginAt() != null) {
            throw new BusinessRuleException("Only sign-ups that were never approved can be declined. Disable this account instead.");
        }
        users.delete(user);
        events.publishEvent(new AccountDeclined(user.getUsername(), user.getDisplayName(), user.getEmail()));
    }

    @Transactional
    public void recordLogin(String username) {
        users.findByUsername(username).ifPresent(u -> u.setLastLoginAt(Instant.now()));
    }

    /** Creates an enabled account if the username is free. Used for the bootstrap admin and demo users. */
    @Transactional
    public boolean createIfMissing(String username, String displayName, String email, String rawPassword,
                                   Role role) {
        String name = normalise(username);
        String mail = email == null || email.isBlank() ? null : normalise(email);
        if (users.existsByUsername(name) || (mail != null && users.existsByEmail(mail))) {
            return false;
        }
        users.save(new AppUser(name, displayName, mail, encoder.encode(rawPassword), role, true));
        return true;
    }

    @Transactional(readOnly = true)
    public boolean hasEnabledAdmin() {
        return users.countByRoleAndEnabledTrue(Role.ADMIN) > 0;
    }

    public static UserDto toDto(AppUser u) {
        return new UserDto(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(), u.isEmailVerified(),
                u.getRole(), u.isEnabled(), u.getCreatedAt(), u.getLastLoginAt());
    }
}
