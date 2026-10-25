package com.example.aml.security;

import org.springframework.security.authentication.AccountStatusException;

/** Correct password, but the email address has not been confirmed yet. */
class EmailNotVerifiedException extends AccountStatusException {

    EmailNotVerifiedException() {
        super("Email address not confirmed");
    }
}
