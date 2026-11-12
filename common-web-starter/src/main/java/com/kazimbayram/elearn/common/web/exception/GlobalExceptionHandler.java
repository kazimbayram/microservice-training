package com.kazimbayram.elearn.common.web.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import com.kazimbayram.elearn.common.web.dto.ErrorResponse;
import com.kazimbayram.elearn.common.web.dto.ValidationErrorResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.ConstraintViolationException;
import com.kazimbayram.elearn.common.web.validation.IdCsv;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ValidationErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
		Map<String, String> details = new LinkedHashMap<>();
		exception.getConstraintViolations().forEach(violation ->
			details.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
		return ResponseEntity.badRequest().body(new ValidationErrorResponse("Validation error", details, "400"));
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException exception) {
		return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage(), "400"));
	}

	@ExceptionHandler(RequestValidationException.class)
	public ResponseEntity<ValidationErrorResponse> handleValidation(RequestValidationException exception) {
		return ResponseEntity.badRequest()
			.body(new ValidationErrorResponse("Validation error", exception.getDetails(), "400"));
	}

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ErrorResponse(exception.getMessage(), "404"));
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ErrorResponse> handleConflict(ConflictException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(new ErrorResponse(exception.getMessage(), "409"));
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException exception, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		if (exception.isForReturnValue()) {
			return handleExceptionInternal(exception, null, headers, status, request);
		}
		Map<String, String> details = new LinkedHashMap<>();
		for (var result : exception.getParameterValidationResults()) {
			if (result.getMethodParameter().hasParameterAnnotation(IdCsv.class)) {
				return new ResponseEntity<>(new ErrorResponse(
					result.getResolvableErrors().getFirst().getDefaultMessage(), "400"),
					headers, HttpStatus.BAD_REQUEST);
			}
			if (result.getMethodParameter().hasParameterAnnotation(PathVariable.class)) {
				return new ResponseEntity<>(new ErrorResponse(
					"Invalid value '" + result.getArgument() + "' for ID. Must be a positive integer", "400"),
					headers, HttpStatus.BAD_REQUEST);
			}
			String field = result.getMethodParameter().getParameterName();
			RequestParam parameter = result.getMethodParameter().getParameterAnnotation(RequestParam.class);
			if (parameter != null && !parameter.value().isEmpty()) {
				field = parameter.value();
			}
			for (var error : result.getResolvableErrors()) {
				details.putIfAbsent(field == null ? "id" : field,
					error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage());
			}
		}
		return new ResponseEntity<>(new ValidationErrorResponse("Validation error", details, "400"),
			headers, HttpStatus.BAD_REQUEST);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> details = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
			details.putIfAbsent(error.getField(),
				error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()));
		exception.getBindingResult().getGlobalErrors().forEach(error ->
			details.putIfAbsent(error.getObjectName(),
				error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()));
		return new ResponseEntity<>(new ValidationErrorResponse("Validation error", details, "400"),
			headers, status);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		if (exception instanceof MissingServletRequestParameterException missing) {
			return new ResponseEntity<>(new ValidationErrorResponse("Validation error",
				Map.of(missing.getParameterName(), "Required parameter is missing"), "400"), headers, status);
		}
		if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
			return new ResponseEntity<>(new ValidationErrorResponse("Validation error",
				Map.of(mismatch.getName(), "Invalid parameter value"), "400"), headers, status);
		}
		if (status.is5xxServerError()) {
			LOGGER.error("Failed to process HTTP request", exception);
		}
		HttpStatus httpStatus = HttpStatus.resolve(status.value());
		String message = httpStatus == null ? "Request failed" : httpStatus.getReasonPhrase();
		return new ResponseEntity<>(new ErrorResponse(message, Integer.toString(status.value())), headers, status);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleServerError(Exception exception) {
		LOGGER.error("Failed to process HTTP request", exception);
		return ResponseEntity.internalServerError()
			.body(new ErrorResponse("An unexpected server error occurred", "500"));
	}
}
