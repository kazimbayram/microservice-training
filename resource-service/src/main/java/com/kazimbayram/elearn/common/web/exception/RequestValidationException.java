package com.kazimbayram.elearn.common.web.exception;

import java.util.Map;

public class RequestValidationException extends RuntimeException {

	private final Map<String, String> details;

	public RequestValidationException(String field, String message) {
		this(Map.of(field, message));
	}

	public RequestValidationException(Map<String, String> details) {
		super("Validation error");
		this.details = Map.copyOf(details);
	}

	public Map<String, String> getDetails() {
		return details;
	}
}
