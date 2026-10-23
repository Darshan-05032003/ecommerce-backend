package com.darshan.ecommerce.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class SkuValidator implements ConstraintValidator<ValidSku, String> {

    static final Pattern FORMAT = Pattern.compile("^[A-Z]{2,4}-[A-Z0-9]{2,8}-[0-9]{3,4}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || FORMAT.matcher(value).matches();
    }
}
