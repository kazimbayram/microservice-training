package com.kazimbayram.elearn.song_service.exception;

import com.kazimbayram.elearn.common.web.exception.NotFoundException;

public class SongNotFoundException extends NotFoundException {

	public SongNotFoundException(Long id) {
		super("Song metadata for ID=" + id + " not found");
	}
}
