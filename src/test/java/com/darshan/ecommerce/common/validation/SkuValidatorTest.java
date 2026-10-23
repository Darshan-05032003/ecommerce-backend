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

class SkuValidatorTest {

    private final SkuValidator validator = new SkuValidator();

    @ParameterizedTest
    @ValueSource(strings = {"LAP-14-001", "BK-JAVA-001", "MOU-WL-0001", "KEYB-MECH2024-999", "AB-12-123"})
    void acceptsTheDocumentedFormat(String sku) {
        assertThat(validator.isValid(sku, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "lap-14-001",          // lower case
            "L-14-001",            // family too short
            "LAPTO-14-001",        // family too long
            "LAP-1-001",           // variant too short
            "LAP-123456789-001",   // variant too long
            "LAP-14-01",           // sequence too short
            "LAP-14-00001",        // sequence too long
            "LAP-14-A01",          // letters in the sequence
            "LAP14001",            // no separators
            "LAP_14_001",          // wrong separator
            " LAP-14-001",         // leading whitespace
            "LAP-14-001\n",        // trailing newline must not be accepted by $
            ""
    })
    void rejectsAnythingElse(String sku) {
        assertThat(validator.isValid(sku, null)).isFalse();
    }

    @Test
    void leavesNullToOtherConstraints() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void reportsTheConstraintMessageThroughBeanValidation() {
        record Form(@NotBlank @ValidSku String sku) {
        }
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator beanValidator = factory.getValidator();

            Set<ConstraintViolation<Form>> violations = beanValidator.validate(new Form("bad sku"));

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).startsWith("must match the SKU format");
            assertThat(beanValidator.validate(new Form("LAP-14-001"))).isEmpty();
        }
    }
}
