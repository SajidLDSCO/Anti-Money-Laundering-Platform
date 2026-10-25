package com.example.aml.api;

import com.example.aml.api.dto.ResendVerificationRequest;
import com.example.aml.api.dto.SignupRequest;
import com.example.aml.api.dto.UserDto;
import com.example.aml.api.dto.VerifyEmailRequest;
import com.example.aml.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-up and the current user. Sign-in ({@code POST /api/auth/login}, form fields username and
 * password) and sign-out ({@code POST /api/auth/logout}) are handled by Spring Security, see SecurityConfig.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService users;

    public AuthController(UserService users) {
        this.users = users;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto signup(@Valid @RequestBody SignupRequest request) {
        return users.signup(request);
    }

    /** The link in the confirmation email opens the web app, which posts the token here. */
    @PostMapping("/verify-email")
    public UserDto verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return users.verifyEmail(request.token());
    }

    /** Always 202, whether or not the account exists, so it cannot be used to probe for accounts. */
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        users.resendVerification(request.usernameOrEmail());
    }

    @GetMapping("/me")
    public UserDto me(Authentication auth) {
        return users.current(auth.getName());
    }
}
