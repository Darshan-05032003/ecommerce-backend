package com.darshan.ecommerce.common.logging;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskingTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(nullValues = "NULL", value = {
            "darshan@example.com, d***@example.com",
            "a@b.in,              a***@b.in",
            "not-an-email,        ***",
            "@example.com,        ***",
            "NULL,                null"
    })
    void keepsOnlyTheFirstLetterAndTheDomain(String email, String masked) {
        assertThat(LogMasking.email(email)).isEqualTo(masked);
    }
}
