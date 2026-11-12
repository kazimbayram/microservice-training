package com.kazimbayram.elearn.common.web.validation;

import java.math.BigInteger;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IdCsvValidator implements ConstraintValidator<IdCsv, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return reject(context, "IDs are required");
		}
		if (value.length() > 200) {
			return reject(context, "CSV string is too long: received " + value.length()
				+ " characters, maximum allowed is 200");
		}
		for (String token : value.split(",", -1)) {
			if (!token.matches("[0-9]+")) {
				return reject(context, "Invalid ID format: '" + token + "'. Only positive integers are allowed");
			}
			BigInteger id = new BigInteger(token);
			if (id.signum() <= 0 || id.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
				return reject(context, "Invalid ID format: '" + token + "'. Only positive integers are allowed");
			}
		}
		return true;
	}

	private boolean reject(ConstraintValidatorContext context, String message) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
		return false;
	}
}
