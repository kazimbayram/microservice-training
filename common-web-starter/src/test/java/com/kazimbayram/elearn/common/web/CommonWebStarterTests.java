package com.kazimbayram.elearn.common.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.kazimbayram.elearn.common.web.exception.GlobalExceptionHandler;
import com.kazimbayram.elearn.common.web.exception.NotFoundException;
import com.kazimbayram.elearn.common.web.exception.RequestValidationException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = CommonWebStarterTests.TestApplication.class)
class CommonWebStarterTests {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void discoversHandlerWithoutComponentScanningOrExplicitImport() {
		assertNotNull(context.getBean(GlobalExceptionHandler.class));
	}

	@Test
	void validatesRequestDtoUsingStarterDependencies() throws Exception {
		mockMvc.perform(post("/test").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
				{"errorMessage":"Validation error","details":{"name":"Name is required"},"errorCode":"400"}
				"""));
	}

	@Test
	void preservesValidRequestBehavior() throws Exception {
		mockMvc.perform(post("/test").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Song\"}"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"name\":\"Song\"}"));
	}

	@Test
	void mapsSharedNotFoundException() throws Exception {
		mockMvc.perform(get("/test/missing"))
			.andExpect(status().isNotFound())
			.andExpect(content().json("{\"errorMessage\":\"Item not found\",\"errorCode\":\"404\"}"));
	}

	@Test
	void mapsSharedValidationException() throws Exception {
		mockMvc.perform(get("/test/invalid"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
				{"errorMessage":"Validation error","details":{"id":"Invalid ID"},"errorCode":"400"}
				"""));
	}

	@Test
	void hidesUnexpectedExceptionDetails() throws Exception {
		mockMvc.perform(get("/test/failure"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().json("""
				{"errorMessage":"An unexpected server error occurred","errorCode":"500"}
				"""));
	}

	@Test
	void hidesMalformedJsonDetails() throws Exception {
		mockMvc.perform(post("/test").contentType(MediaType.APPLICATION_JSON).content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"errorMessage\":\"Bad Request\",\"errorCode\":\"400\"}"));
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(TestController.class)
	static class TestApplication {
	}

	public record TestRequest(@NotBlank(message = "Name is required") String name) {
	}

	@RestController
	static class TestController {

		@PostMapping("/test")
		public TestRequest create(@Valid @RequestBody TestRequest request) {
			return request;
		}

		@GetMapping("/test/missing")
		public void missing() {
			throw new NotFoundException("Item not found");
		}

		@GetMapping("/test/invalid")
		public void invalid() {
			throw new RequestValidationException(Map.of("id", "Invalid ID"));
		}

		@GetMapping("/test/failure")
		public void failure() {
			throw new IllegalStateException("Internal database details");
		}
	}
}
