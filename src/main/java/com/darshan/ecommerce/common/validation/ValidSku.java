package com.darshan.ecommerce.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stock keeping unit in the form FAMILY-VARIANT-SEQUENCE, for example LAP-14-001 or BK-JAVA-001:
 * two to four upper case letters, two to eight upper case letters or digits, then three or four
 * digits, separated by hyphens. Null is left to @NotNull or @NotBlank.
 */
@Documented
@Constraint(validatedBy = SkuValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSku {

    String message() default "must match the SKU format FAMILY-VARIANT-SEQUENCE, "
            + "for example LAP-14-001 (upper case letters and digits only)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
