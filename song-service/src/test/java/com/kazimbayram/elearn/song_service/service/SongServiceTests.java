package com.kazimbayram.elearn.song_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import jakarta.validation.ConstraintViolationException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import com.kazimbayram.elearn.song_service.dto.SongDto;
import com.kazimbayram.elearn.song_service.entity.Song;
import com.kazimbayram.elearn.song_service.exception.SongAlreadyExistsException;
import com.kazimbayram.elearn.song_service.exception.SongNotFoundException;
import com.kazimbayram.elearn.song_service.mapper.SongMapper;
import com.kazimbayram.elearn.song_service.repository.SongRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class SongServiceTests {

	private final SongRepository repository = mock(SongRepository.class);
	private final SongMapper mapper = new SongMapper();
	private final SongService service = validatedService();

	private SongService validatedService() {
		ProxyFactory factory = new ProxyFactory(new SongService(repository, mapper));
		factory.setProxyTargetClass(true);
		factory.addAdvice(new MethodValidationInterceptor());
		return (SongService) factory.getProxy();
	}
	private final SongDto song = new SongDto(7L, "  Original name  ", "Queen", "News of the world", "02:59", "1977");

	@Test
	void insertsWithSuppliedIdWithoutCheckingResourceServiceOrChangingMetadata() {
		assertEquals(7L, service.create(song));

		ArgumentCaptor<Song> captor = ArgumentCaptor.forClass(Song.class);
		verify(repository).saveAndFlush(captor.capture());
		assertEquals(song, mapper.toDto(captor.getValue()));
		org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().isNew());
	}

	@Test
	void rejectsDuplicateWithoutOverwritingExistingRecord() {
		when(repository.existsById(7L)).thenReturn(true);

		assertThrows(SongAlreadyExistsException.class, () -> service.create(song));

		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void mapsConcurrentDuplicateInsertToConflict() {
		when(repository.saveAndFlush(any())).thenThrow(
			new DataIntegrityViolationException("Duplicate key", new SQLException("Duplicate key", "23505")));

		assertThrows(SongAlreadyExistsException.class, () -> service.create(song));
	}

	@Test
	void doesNotMisreportOtherDatabaseErrorsAsDuplicateIds() {
		when(repository.saveAndFlush(any())).thenThrow(
			new DataIntegrityViolationException("Database failure", new SQLException("Not null", "23502")));

		assertThrows(DataIntegrityViolationException.class, () -> service.create(song));
	}

	@Test
	void retrievesMetadataWithoutChangingIt() {
		when(repository.findById(7L)).thenReturn(Optional.of(mapper.toEntity(song)));

		assertEquals(song, service.get("7"));
	}

	@Test
	void reportsMissingMetadata() {
		when(repository.findById(7L)).thenReturn(Optional.empty());

		assertThrows(SongNotFoundException.class, () -> service.get("7"));
	}

	@Test
	void deletesOnlyExistingIdsOnceInRequestOrder() {
		Song entity = mapper.toEntity(song);
		when(repository.findById(7L)).thenReturn(Optional.of(entity));
		when(repository.findById(8L)).thenReturn(Optional.empty());

		assertEquals(List.of(7L), service.deleteAll("8,7,7"));

		verify(repository).delete(entity);
		verify(repository).findById(7L);
	}

	@Test
	void ignoresMissingMetadata() {
		when(repository.findById(7L)).thenReturn(Optional.empty());

		assertEquals(List.of(), service.deleteAll("7"));

		verify(repository, never()).delete(any());
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "1.5", "-1", "0", "2147483648", "999999999999999999999" })
	void rejectsInvalidGetIdsBeforeQueryingDatabase(String id) {
		assertThrows(ConstraintViolationException.class, () -> service.get(id));
		verifyNoInteractions(repository);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "7,abc", "7,1.5", "7,-1", "7,0", "7,2147483648", "7,", ",7", "7,,8", "7, 8" })
	void validatesAllIdsBeforeDeletingAnything(String csv) {
		assertThrows(ConstraintViolationException.class, () -> service.deleteAll(csv));
		verifyNoInteractions(repository);
	}

	@Test
	void enforcesCsvLengthBoundary() {
		assertEquals(List.of(), service.deleteAll("1,".repeat(99) + "10"));
		assertThrows(ConstraintViolationException.class, () -> service.deleteAll("1,".repeat(100) + "1"));
	}
}
