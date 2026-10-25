package com.example.aml.security;

import com.example.aml.domain.AppUser;
import com.example.aml.service.UserService;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Loads accounts for sign-in by username or email. Deliberately not a Spring bean: only
 * {@link SecurityConfig}'s authentication provider uses it, so no second provider with default checks gets created.
 * <p>
 * The principal's name is always the username, whichever was typed, so notes and SARs are stamped consistently.
 */
class AppUserDetailsService implements UserDetailsService {

    private final UserService users;

    AppUserDetailsService(UserService users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) {
        AppUser u = users.findForSignIn(usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException("No such user"));
        return new AppUserDetails(u);
    }

    /** A signed-in user, plus whether the email is confirmed (checked after the password, see SecurityConfig). */
    static final class AppUserDetails extends User {

        private final boolean emailVerified;

        AppUserDetails(AppUser u) {
            super(u.getUsername(), u.getPasswordHash(), u.isEnabled(), true, true, true,
                    AuthorityUtils.createAuthorityList("ROLE_" + u.getRole().name()));
            this.emailVerified = u.isEmailVerified();
        }

        boolean emailVerified() {
            return emailVerified;
        }
    }
}
