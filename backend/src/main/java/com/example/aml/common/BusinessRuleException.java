package com.example.aml.common;

/** A request that is well-formed but not allowed in the current state (HTTP 409). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
