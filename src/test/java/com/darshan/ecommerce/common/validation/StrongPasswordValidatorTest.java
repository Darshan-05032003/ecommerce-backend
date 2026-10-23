package com.darshan.ecommerce.common.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "Short1!",            // 7 characters
            "alllowercase1!",     // no upper case
            "ALLUPPERCASE1!",     // no lower case
            "NoDigitsHere!",      // no digit
            "NoSpecial123",       // no special character
            "        ",           // only whitespace
            "Spaces Are 1 ok"     // whitespace does not count as special
    })
    void rejectsPasswordsMissingARequirement(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Str0ng!pw",
            "Correct-Horse-9",
            "Pa55word#",
            "Ünïcödé1!",
            "A1b2c3d4$"
    })
    void acceptsPasswordsMeetingEveryRequirement(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @Test
    void leavesNullToOtherConstraints() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void reportsASingleViolationWithTheConstraintMessageThroughBeanValidation() {
        record Form(@NotBlank @StrongPassword String password) {
        }
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator beanValidator = factory.getValidator();

            Set<ConstraintViolation<Form>> violations = beanValidator.validate(new Form("weak"));

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).startsWith("must be at least 8 characters");
            assertThat(beanValidator.validate(new Form("Str0ng!pw"))).isEmpty();
        }
    }
}
