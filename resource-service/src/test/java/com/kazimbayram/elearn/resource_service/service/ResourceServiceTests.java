package com.kazimbayram.elearn.resource_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.Optional;

import com.kazimbayram.elearn.resource_service.entity.Resource;
import com.kazimbayram.elearn.resource_service.client.SongApiClient;
import jakarta.validation.ConstraintViolationException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import com.kazimbayram.elearn.resource_service.repository.ResourceRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResourceServiceTests {

	private final ResourceRepository repository = mock(ResourceRepository.class);
	private final SongApiClient songClient = mock(SongApiClient.class);
	private final ResourceService service = validatedService();

	private ResourceService validatedService() {
		ProxyFactory factory = new ProxyFactory(new ResourceService(repository, songClient));
		factory.setProxyTargetClass(true);
		factory.addAdvice(new MethodValidationInterceptor());
		return (ResourceService) factory.getProxy();
	}

	@Test
	void deletesExistingResourcesOnceInRequestOrderAndIgnoresMissingOnes() {
		Resource first = new Resource("first.mp3", "audio/mpeg", new byte[] { 1 });
		Resource third = new Resource("third.mp3", "audio/mpeg", new byte[] { 3 });
		when(repository.findById(1L)).thenReturn(Optional.of(first));
		when(repository.findById(2L)).thenReturn(Optional.empty());
		when(repository.findById(3L)).thenReturn(Optional.of(third));

		assertEquals(List.of(3L, 1L), service.deleteAll("3,2,1,3"));

		verify(repository).findById(3L);
		verify(repository).delete(first);
		verify(repository).delete(third);
		verify(repository).flush();
		verify(songClient).deleteAll(List.of(3L, 1L));
	}

	@Test
	void returnsEmptyListWhenNoResourcesExist() {
		when(repository.findById(1L)).thenReturn(Optional.empty());

		assertEquals(List.of(), service.deleteAll("1"));

		verify(repository, never()).delete(org.mockito.ArgumentMatchers.any(Resource.class));
		verifyNoInteractions(songClient);
	}

	@Test
	void propagatesSongDeletionFailureSoTransactionCanRollBack() {
		Resource resource = new Resource("song.mp3", "audio/mpeg", new byte[] { 1 });
		when(repository.findById(1L)).thenReturn(Optional.of(resource));
		doThrow(new IllegalStateException("Song Service unavailable")).when(songClient).deleteAll(List.of(1L));

		assertThrows(IllegalStateException.class, () -> service.deleteAll("1"));
	}

	@Test
	void deletesMetadataForSingleResourceDeletion() {
		when(repository.existsById(1L)).thenReturn(true);

		service.delete(1L);

		verify(repository).deleteById(1L);
		verify(repository).flush();
		verify(songClient).deleteAll(List.of(1L));
	}

	@Test
	void propagatesDatabaseErrors() {
		when(repository.findById(1L)).thenThrow(new IllegalStateException("Database unavailable"));

		assertThrows(IllegalStateException.class, () -> service.deleteAll("1"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "abc", "-1", "0", "1.5", "2147483648", "1,2" })
	void validatesGetIdBeforeAccessingDatabase(String id) {
		assertThrows(ConstraintViolationException.class, () -> service.getAudio(id));
		verifyNoInteractions(repository);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "abc", "1,", ",1", "1,,2", "1, 2", "1,0", "1,2147483648" })
	void validatesEntireCsvBeforeDeletingAnything(String csv) {
		assertThrows(ConstraintViolationException.class, () -> service.deleteAll(csv));
		verifyNoInteractions(repository);
	}

	@Test
	void rejectsCsvOver200CharactersBeforeAccessingDatabase() {
		assertThrows(ConstraintViolationException.class, () -> service.deleteAll("1,".repeat(100) + "1"));
		verifyNoInteractions(repository);
	}

	@Test
	void retrievesAudioInsideServiceTransaction() {
		byte[] audio = { 1, 2, 3 };
		when(repository.findById(1L)).thenReturn(Optional.of(new Resource("audio.mp3", "audio/mpeg", audio)));

		org.junit.jupiter.api.Assertions.assertArrayEquals(audio, service.getAudio("1"));
	}
}
