package com.example.aml.security;

import com.example.aml.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Session-cookie authentication for the Angular app, with CSRF protection.
 * <p>
 * Who may do what (each role also has the rights of the roles before it):
 * <ul>
 *   <li>anyone: sign in, sign up, health check</li>
 *   <li>ANALYST: everything else not listed below</li>
 *   <li>MLRO: file SARs, freeze and unfreeze accounts</li>
 *   <li>ADMIN: manage users, switch detection rules, maintain the sanctions list, H2 console</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * The account checks run after the password check, not before as by default, so a wrong
     * password on a pending account gets the same answer as on an unknown username.
     * An unconfirmed email is reported first: the admin only sees the account once it is confirmed.
     */
    @Bean
    DaoAuthenticationProvider authenticationProvider(UserService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(new AppUserDetailsService(users));
        provider.setPasswordEncoder(encoder);
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(user -> {
            if (user instanceof AppUserDetailsService.AppUserDetails details && !details.emailVerified()) {
                throw new EmailNotVerifiedException();
            }
            if (!user.isEnabled()) {
                throw new DisabledException("Account not yet approved");
            }
        });
        return provider;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JsonAuthResponses responses) throws Exception {
        CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokens.setCookieCustomizer(c -> c.path("/").sameSite("Lax"));
        RequestMatcher h2Console = PATHS.matcher("/h2-console/**");

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers(h2Console))
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PATHS.matcher(HttpMethod.POST, "/api/auth/login"),
                                PATHS.matcher(HttpMethod.POST, "/api/auth/signup"),
                                PATHS.matcher(HttpMethod.POST, "/api/auth/verify-email"),
                                PATHS.matcher(HttpMethod.POST, "/api/auth/resend-verification"),
                                PATHS.matcher("/actuator/health"),
                                PATHS.matcher("/error")).permitAll()
                        .requestMatchers(h2Console).hasRole("ADMIN")
                        .requestMatchers(PATHS.matcher("/api/users/**"),
                                PATHS.matcher(HttpMethod.PATCH, "/api/rules/**"),
                                PATHS.matcher(HttpMethod.POST, "/api/sanctions/**"),
                                PATHS.matcher(HttpMethod.DELETE, "/api/sanctions/**")).hasRole("ADMIN")
                        .requestMatchers(PATHS.matcher(HttpMethod.POST, "/api/alerts/*/sar"),
                                PATHS.matcher(HttpMethod.PATCH, "/api/accounts/*/status"))
                        .hasAnyRole("MLRO", "ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler(responses::loginSucceeded)
                        .failureHandler(responses::loginFailed))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(responses::unauthenticated)
                        .accessDeniedHandler(responses::forbidden))
                .requestCache(cache -> cache.disable())
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
