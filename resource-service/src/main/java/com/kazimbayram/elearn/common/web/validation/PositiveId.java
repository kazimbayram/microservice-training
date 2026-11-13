package com.kazimbayram.elearn.common.web.validation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.ReportAsSingleViolation;

@Target({ ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@NotNull
@Pattern(regexp = "[0-9]+")
@DecimalMin("1")
@DecimalMax("2147483647")
@ReportAsSingleViolation
public @interface PositiveId {

	String message() default "ID must be a positive integer between 1 and 2147483647";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
