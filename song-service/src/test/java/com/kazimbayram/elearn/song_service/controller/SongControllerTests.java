package com.kazimbayram.elearn.song_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kazimbayram.elearn.common.web.exception.GlobalExceptionHandler;
import com.kazimbayram.elearn.song_service.service.SongService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SongControllerTests {

	private SongService service;
	private MockMvc mockMvc;

	private static final String SERVER_ERROR = """
		{"errorMessage":"An unexpected server error occurred","errorCode":"500"}
		""";

	@BeforeEach
	void setUp() {
		service = mock(SongService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new SongController(service))
			.setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test
	void hidesUnexpectedCreateFailure() throws Exception {
		when(service.create(any())).thenThrow(new IllegalStateException("Internal database details"));

		mockMvc.perform(post("/songs").contentType(MediaType.APPLICATION_JSON).content("""
				{"id":1,"name":"Song","artist":"Artist","album":"Album","duration":"02:59","year":"1977"}
				"""))
			.andExpect(status().isInternalServerError())
			.andExpect(content().json(SERVER_ERROR));
	}

	@Test
	void hidesUnexpectedGetFailure() throws Exception {
		when(service.get("1")).thenThrow(new IllegalStateException("Internal database details"));

		mockMvc.perform(get("/songs/1"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().json(SERVER_ERROR));
	}

	@Test
	void hidesUnexpectedDeleteFailure() throws Exception {
		when(service.deleteAll("1")).thenThrow(new IllegalStateException("Internal database details"));

		mockMvc.perform(delete("/songs").param("id", "1"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().json(SERVER_ERROR));
	}
}
