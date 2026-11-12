package com.kazimbayram.elearn.resource_service.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.kazimbayram.elearn.common.web.exception.GlobalExceptionHandler;
import com.kazimbayram.elearn.resource_service.client.SongApiClient;
import com.kazimbayram.elearn.resource_service.exception.ResourceNotFoundException;
import com.kazimbayram.elearn.resource_service.service.ResourceService;
import com.kazimbayram.elearn.resource_service.service.ResourceUploadService;
import com.kazimbayram.elearn.resource_service.repository.ResourceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ResourceControllerTests {

	private ResourceService resourceService;
	private ResourceRepository repository;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		repository = mock(ResourceRepository.class);
		resourceService = spy(new ResourceService(repository, mock(SongApiClient.class)));
		mockMvc = MockMvcBuilders.standaloneSetup(new ResourceController(resourceService, mock(ResourceUploadService.class)))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void returnsOriginalAudioBytes() throws Exception {
		byte[] audio = { 73, 68, 51, 0, -1, 42 };
		doReturn(audio).when(resourceService).getAudio("1");

		mockMvc.perform(get("/resources/1"))
			.andExpect(status().isOk())
			.andExpect(content().contentType("audio/mpeg"))
			.andExpect(content().bytes(audio));

		verify(resourceService).getAudio("1");
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "1.5", "-1", "0", "2147483648", "999999999999999999999" })
	void rejectsInvalidIdsWithoutCallingService(String id) throws Exception {
		mockMvc.perform(get("/resources/{id}", id))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
				{"errorMessage":"Invalid value '%s' for ID. Must be a positive integer","errorCode":"400"}
				""".formatted(id)));

		verifyNoInteractions(repository);
	}

	@Test
	void returnsNotFoundForMissingResource() throws Exception {
		doThrow(new ResourceNotFoundException(1L)).when(resourceService).getAudio("1");

		mockMvc.perform(get("/resources/1"))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
				{"errorMessage":"Resource with ID=1 not found","errorCode":"404"}
				"""));
	}

	@Test
	void returnsInternalServerErrorWithoutExposingDetails() throws Exception {
		doThrow(new IllegalStateException("Database unavailable")).when(resourceService).getAudio("1");

		mockMvc.perform(get("/resources/1"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().json("""
				{"errorMessage":"An unexpected server error occurred","errorCode":"500"}
				"""));
	}

	@Test
	void returnsOnlyDeletedIds() throws Exception {
		doReturn(List.of(1L, 3L)).when(resourceService).deleteAll("1,2,3");

		mockMvc.perform(delete("/resources").param("id", "1,2,3"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"ids\":[1,3]}"));
	}

	@Test
	void returnsEmptyIdsWhenNoResourcesExist() throws Exception {
		doReturn(List.of()).when(resourceService).deleteAll("1");

		mockMvc.perform(delete("/resources").param("id", "1"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"ids\":[]}"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "abc", "1.5", "-1", "0", "1,0", "1,,2", ",1", "1,", "1, 2",
			"1;2", "+1", "2147483648", "999999999999999999999" })
	void rejectsInvalidDeleteCsv(String ids) throws Exception {
		mockMvc.perform(delete("/resources").param("id", ids))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(repository);
	}

	@Test
	void rejectsMissingDeleteParameter() throws Exception {
		mockMvc.perform(delete("/resources"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
				{"errorMessage":"Validation error","details":{"id":"Required parameter is missing"},"errorCode":"400"}
				"""));

		verifyNoInteractions(repository);
	}

	@Test
	void acceptsCsvOfExactly200Characters() throws Exception {
		String ids = "1,".repeat(99) + "10";

		mockMvc.perform(delete("/resources").param("id", ids))
			.andExpect(status().isOk());

		verify(resourceService).deleteAll(ids);
	}

	@Test
	void rejectsCsvOver200Characters() throws Exception {
		mockMvc.perform(delete("/resources").param("id", "1,".repeat(100) + "1"))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(repository);
	}

	@Test
	void returnsServerErrorWhenDeletionFails() throws Exception {
		doThrow(new IllegalStateException("Database unavailable")).when(resourceService).deleteAll("1");

		mockMvc.perform(delete("/resources").param("id", "1"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().json("""
				{"errorMessage":"An unexpected server error occurred","errorCode":"500"}
				"""));
	}

	@Test
	void preservesMethodNotAllowedStatusWithUnifiedErrorBody() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/resources/1"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(content().json("""
				{"errorMessage":"Method Not Allowed","errorCode":"405"}
				"""));
	}
}
