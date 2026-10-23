package com.darshan.ecommerce.common.observability;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marks a bean method, or every public method of a bean class, whose calls are logged when they
// take longer than app.observability.slow-method-threshold. Only calls that go through the Spring
// proxy are measured: a call from inside the same bean (this.method()) is not.
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogExecutionTime {
}
