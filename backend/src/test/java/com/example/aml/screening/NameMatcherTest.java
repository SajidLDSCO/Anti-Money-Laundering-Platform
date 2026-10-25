package com.example.aml.screening;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NameMatcherTest {

    private static final double THRESHOLD = 0.88;

    @ParameterizedTest(name = "{0} ~ {1}")
    @CsvSource({
            "Golden Crescent Trading LLC, Golden Cresent Trading",   // typo + legal suffix
            "Ivan Petrenko, Ivan Petrenkov",                         // transliteration drift
            "Ivan Petrenko, Petrenko Ivan",                          // word order
            "Viktor Petrovich Ivanenko, Viktor Ivanenko",            // dropped patronymic
            "Mohammed Rahman, Mohammad Rahman",                      // spelling variant
            "José Ramírez, Jose Ramirez",                            // accents
            "Al Noor Exchange House, Al-Nour Exchange"               // punctuation + extra word
    })
    void shouldMatch(String listed, String candidate) {
        assertThat(NameMatcher.similarity(listed, candidate)).isGreaterThanOrEqualTo(THRESHOLD);
    }

    @ParameterizedTest(name = "{0} !~ {1}")
    @CsvSource({
            "Ivan Petrenko, Ivan Petrov",             // shared first name, different surname
            "Abdul Karim, Abdul Rahim",
            "Daniel Okafor, Daniel Okoro",
            "Golden Crescent Trading LLC, Golden Gate Trading",
            "Northwind Import Export, Northern Imports",
            "John Smith, Jane Smithers"
    })
    void shouldNotMatch(String listed, String candidate) {
        assertThat(NameMatcher.similarity(listed, candidate)).isLessThan(THRESHOLD);
    }

    @org.junit.jupiter.api.Test
    void normalizeStripsNoise() {
        assertThat(NameMatcher.normalize("The Golden Crescent Trading, L.L.C.")).isEqualTo("golden crescent trading");
        assertThat(NameMatcher.normalize("Dr. Hassan  Ali")).isEqualTo("hassan ali");
    }
}
