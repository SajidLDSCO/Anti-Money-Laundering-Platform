package com.example.aml.common;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Human-friendly business identifiers such as ALT-261024-7KQ2M. */
public final class Ids {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

    private Ids() {
    }

    public static String next(String prefix) {
        StringBuilder sb = new StringBuilder(prefix).append('-')
                .append(LocalDate.now(ZoneOffset.UTC).format(DAY)).append('-');
        for (int i = 0; i < 5; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public static String digits(String prefix, int length) {
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
