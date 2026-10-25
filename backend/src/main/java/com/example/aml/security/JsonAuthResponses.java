package com.example.aml.security;

import com.example.aml.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** JSON answers for sign-in and for requests the security filters reject, in the API's problem-detail format. */
@Component
class JsonAuthResponses {

    private final ObjectMapper json;
    private final UserService users;

    JsonAuthResponses(ObjectMapper json, UserService users) {
        this.json = json;
        this.users = users;
    }

    void loginSucceeded(HttpServletRequest request, HttpServletResponse response, Authentication auth)
            throws IOException {
        users.recordLogin(auth.getName());
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), users.current(auth.getName()));
    }

    void loginFailed(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        // The account-status answers are only given once the password has been checked (see SecurityConfig),
        // so they do not reveal which usernames exist. "code" lets the sign-in page offer the right next step.
        if (ex instanceof EmailNotVerifiedException) {
            problem(response, HttpStatus.UNAUTHORIZED,
                    "Confirm your email address first: open the link we emailed you.", "EMAIL_NOT_VERIFIED");
        } else if (ex instanceof DisabledException) {
            problem(response, HttpStatus.UNAUTHORIZED,
                    "Your account is waiting for an administrator to approve it.", "APPROVAL_PENDING");
        } else {
            problem(response, HttpStatus.UNAUTHORIZED, "Incorrect username, email or password.");
        }
    }

    void unauthenticated(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        problem(response, HttpStatus.UNAUTHORIZED, "Sign in to continue.");
    }

    void forbidden(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        problem(response, HttpStatus.FORBIDDEN, ex instanceof CsrfException
                ? "The request could not be verified. Reload the page and try again."
                : "Your role does not allow this action.");
    }

    private void problem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        problem(response, status, detail, null);
    }

    private void problem(HttpServletResponse response, HttpStatus status, String detail, String code)
            throws IOException {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        if (code != null) {
            body.setProperty("code", code);
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), body);
    }
}
