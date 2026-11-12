package com.kazimbayram.elearn.song_service.exception;

import com.kazimbayram.elearn.common.web.exception.ConflictException;

public class SongAlreadyExistsException extends ConflictException {

	public SongAlreadyExistsException(Long id) {
		super("Metadata for resource ID=" + id + " already exists");
	}
}
