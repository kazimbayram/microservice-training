package com.kazimbayram.elearn.common.web.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IdCsvValidator.class)
public @interface IdCsv {

	String message() default "IDs must be comma-separated positive integers without spaces; maximum length is 200 characters";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
