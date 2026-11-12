package com.kazimbayram.elearn.resource_service.exception;

import com.kazimbayram.elearn.resource_service.client.SongApiError;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientException;

public class SongApiException extends RestClientException {

	private final HttpStatusCode status;
	private final SongApiError error;

	public SongApiException(HttpStatusCode status, SongApiError error) {
		super("Song Service returned " + status.value() + ": " + error.errorMessage()
			+ " (errorCode=" + error.errorCode() + ")"
			+ (error.details() == null ? "" : "; details=" + error.details()));
		this.status = status;
		this.error = error;
	}

	public HttpStatusCode getStatus() {
		return status;
	}

	public SongApiError getError() {
		return error;
	}
}
