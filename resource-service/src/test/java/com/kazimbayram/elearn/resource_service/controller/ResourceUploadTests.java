package com.kazimbayram.elearn.resource_service.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kazimbayram.elearn.common.web.exception.GlobalExceptionHandler;
import com.kazimbayram.elearn.resource_service.client.SongApiClient;
import com.kazimbayram.elearn.resource_service.dto.SongMetadataRequest;
import com.kazimbayram.elearn.resource_service.entity.Resource;
import com.kazimbayram.elearn.resource_service.mapper.SongMetadataMapper;
import com.kazimbayram.elearn.resource_service.metadata.AudioMetadataExtractor;
import com.kazimbayram.elearn.resource_service.repository.ResourceRepository;
import com.kazimbayram.elearn.resource_service.service.ResourceService;
import com.kazimbayram.elearn.resource_service.service.ResourceUploadService;
import com.kazimbayram.elearn.resource_service.support.Mp3Fixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClientException;

class ResourceUploadTests {

	private final ResourceRepository repository = mock(ResourceRepository.class);
	private final SongApiClient songClient = mock(SongApiClient.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		ResourceUploadService uploads = new ResourceUploadService(repository, new AudioMetadataExtractor(),
			new SongMetadataMapper(), songClient);
		mockMvc = MockMvcBuilders.standaloneSetup(new ResourceController(mock(ResourceService.class), uploads))
			.setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	private void persistedResource() {
		Resource resource = mock(Resource.class);
		when(resource.getId()).thenReturn(7L);
		when(repository.saveAndFlush(any())).thenReturn(resource);
	}

	@ParameterizedTest
	@ValueSource(strings = { "audio/mpeg", "audio/mpeg; charset=UTF-8", "Audio/MPEG" })
	void storesOriginalBytesAndSendsUnmodifiedMetadata(String contentType) throws Exception {
		persistedResource();
		byte[] audio = Mp3Fixture.audio();

		mockMvc.perform(post("/resources").contentType(contentType).content(audio))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"id\":7}"));

		ArgumentCaptor<Resource> saved = ArgumentCaptor.forClass(Resource.class);
		verify(repository).saveAndFlush(saved.capture());
		assertArrayEquals(audio, saved.getValue().getData());
		ArgumentCaptor<SongMetadataRequest> sent = ArgumentCaptor.forClass(SongMetadataRequest.class);
		verify(songClient).create(sent.capture());
		assertEquals(new SongMetadataRequest(7L, "  Original title  ", "Queen", "News of the world",
			"00:02", "1977"), sent.getValue());
	}

	@ParameterizedTest
	@ValueSource(strings = { "application/json", "text/plain", "application/octet-stream", "audio/wav" })
	void rejectsWrongContentTypeWithExactErrorContract(String contentType) throws Exception {
		mockMvc.perform(post("/resources").contentType(contentType).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
				{"errorMessage":"Invalid file format: %s. Only MP3 files are allowed","errorCode":"400"}
				""".formatted(contentType)));

		verifyNoInteractions(repository, songClient);
	}

	@Test
	void rejectsMissingContentType() throws Exception {
		mockMvc.perform(post("/resources").content(Mp3Fixture.audio()))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
				{"errorMessage":"Invalid file format: missing. Only MP3 files are allowed","errorCode":"400"}
				"""));
		verifyNoInteractions(repository, songClient);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "garbage", "{\"name\":\"not audio\"}", "ID3" })
	void rejectsInvalidMp3BeforeStoringAnything(String body) throws Exception {
		mockMvc.perform(post("/resources").contentType("audio/mpeg").content(body))
			.andExpect(status().isBadRequest())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode").value("400"));

		verifyNoInteractions(repository, songClient);
	}

	@Test
	void returns500WhenSongServiceRejectsMetadata() throws Exception {
		persistedResource();
		doThrow(new RestClientException("Song Service failed")).when(songClient).create(any());

		mockMvc.perform(post("/resources").contentType("audio/mpeg").content(Mp3Fixture.audio()))
			.andExpect(status().isInternalServerError())
			.andExpect(content().json("""
				{"errorMessage":"An unexpected server error occurred","errorCode":"500"}
				"""));
	}

	@Test
	void returns500WhenDatabaseFailsWithoutCallingSongService() throws Exception {
		when(repository.saveAndFlush(any())).thenThrow(new IllegalStateException("Database unavailable"));

		mockMvc.perform(post("/resources").contentType("audio/mpeg").content(Mp3Fixture.audio()))
			.andExpect(status().isInternalServerError());
		verifyNoInteractions(songClient);
	}
}
