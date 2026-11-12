package com.kazimbayram.elearn.song_service.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import com.kazimbayram.elearn.common.web.validation.PositiveId;
import com.kazimbayram.elearn.common.web.validation.IdCsv;
import com.kazimbayram.elearn.song_service.dto.SongDto;
import com.kazimbayram.elearn.song_service.exception.SongAlreadyExistsException;
import com.kazimbayram.elearn.song_service.exception.SongNotFoundException;
import com.kazimbayram.elearn.song_service.mapper.SongMapper;
import com.kazimbayram.elearn.song_service.repository.SongRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class SongService {

	private final SongRepository repository;
	private final SongMapper mapper;

	public SongService(SongRepository repository, SongMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	public Long create(SongDto dto) {
		if (repository.existsById(dto.id())) {
			throw new SongAlreadyExistsException(dto.id());
		}
		try {
			repository.saveAndFlush(mapper.toEntity(dto));
		}
		catch (DataIntegrityViolationException exception) {
			for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
				if (cause instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
					throw new SongAlreadyExistsException(dto.id());
				}
			}
			throw exception;
		}
		return dto.id();
	}

	@Transactional(readOnly = true)
	public SongDto get(@PositiveId String id) {
		Long songId = Long.valueOf(id);
		return repository.findById(songId).map(mapper::toDto)
			.orElseThrow(() -> new SongNotFoundException(songId));
	}

	public List<Long> deleteAll(@IdCsv String csv) {
		List<Long> ids = Arrays.stream(csv.split(",")).map(Long::valueOf).toList();
		List<Long> deletedIds = new ArrayList<>();
		for (Long id : new LinkedHashSet<>(ids)) {
			repository.findById(id).ifPresent(song -> {
				repository.delete(song);
				deletedIds.add(id);
			});
		}
		return deletedIds;
	}
}
