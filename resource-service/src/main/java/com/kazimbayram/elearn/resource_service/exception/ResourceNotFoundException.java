package com.kazimbayram.elearn.resource_service.exception;

import com.kazimbayram.elearn.common.web.exception.NotFoundException;

public class ResourceNotFoundException extends NotFoundException {

	public ResourceNotFoundException(Long id) {
		super("Resource with ID=" + id + " not found");
	}
}
