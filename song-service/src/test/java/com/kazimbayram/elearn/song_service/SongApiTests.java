package com.kazimbayram.elearn.song_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import com.kazimbayram.elearn.song_service.dto.SongDto;
import com.kazimbayram.elearn.song_service.entity.Song;
import com.kazimbayram.elearn.song_service.mapper.SongMapper;
import com.kazimbayram.elearn.song_service.repository.SongRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SongApiTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private SongRepository repository;

	@Autowired
	private SongMapper songMapper;

	@Autowired
	private ObjectMapper json;

	private MockMvc mockMvc;
	private long id;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		do {
			id = ThreadLocalRandom.current().nextLong(1_000_000_000L, Integer.MAX_VALUE - 1L);
		} while (repository.existsById(id) || repository.existsById(id + 1));
	}

	private Map<String, Object> metadata() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", id);
		body.put("name", "We are the champions");
		body.put("artist", "Queen");
		body.put("album", "News of the world");
		body.put("duration", "02:59");
		body.put("year", "1977");
		return body;
	}

	@Test
	void createsStandaloneMetadataUsingResourceIdAndRetrievesExactFields() throws Exception {
		String body = json.writeValueAsString(metadata());

		mockMvc.perform(post("/songs").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"id\":" + id + "}"));

		mockMvc.perform(get("/songs/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().json(body));

		Song persisted = repository.findById(id).orElseThrow();
		assertFalse(persisted.isNew());
		assertEquals(id, persisted.getId());
	}

	@Test
	void returnsConflictWithoutReplacingMetadata() throws Exception {
		SongDto original = new SongDto(id, "Original", "Queen", "Album", "02:59", "1977");
		repository.saveAndFlush(songMapper.toEntity(original));

		mockMvc.perform(post("/songs").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(metadata())))
			.andExpect(status().isConflict())
			.andExpect(content().json("""
				{"errorMessage":"Metadata for resource ID=%d already exists","errorCode":"409"}
				""".formatted(id)));

		assertEquals(original, songMapper.toDto(repository.findById(id).orElseThrow()));
	}

	@ParameterizedTest
	@ValueSource(strings = { "id", "name", "artist", "album", "duration", "year" })
	void rejectsEveryMissingField(String field) throws Exception {
		Map<String, Object> body = metadata();
		body.remove(field);

		mockMvc.perform(post("/songs").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorMessage").value("Validation error"))
			.andExpect(jsonPath("$.errorCode").value("400"))
			.andExpect(jsonPath("$.details." + field).isNotEmpty());
	}

	static Stream<Arguments> invalidMetadata() {
		return Stream.of(
			Arguments.of("id", 0), Arguments.of("id", -1), Arguments.of("id", 2147483648L),
			Arguments.of("name", ""), Arguments.of("name", " "), Arguments.of("name", "a".repeat(101)),
			Arguments.of("artist", ""), Arguments.of("artist", " "), Arguments.of("artist", "a".repeat(101)),
			Arguments.of("album", ""), Arguments.of("album", " "), Arguments.of("album", "a".repeat(101)),
			Arguments.of("duration", ""), Arguments.of("duration", "2:59"), Arguments.of("duration", "02:5"),
			Arguments.of("duration", "02:60"), Arguments.of("duration", "100:00"), Arguments.of("duration", "-1:00"),
			Arguments.of("year", ""), Arguments.of("year", "1899"), Arguments.of("year", "2100"),
			Arguments.of("year", "197"), Arguments.of("year", "abcd"));
	}

	@ParameterizedTest
	@MethodSource("invalidMetadata")
	void rejectsInvalidMetadataWithFieldDetails(String field, Object value) throws Exception {
		Map<String, Object> body = metadata();
		body.put(field, value);

		mockMvc.perform(post("/songs").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorMessage").value("Validation error"))
			.andExpect(jsonPath("$.errorCode").value("400"))
			.andExpect(jsonPath("$.details." + field).isNotEmpty());
	}

	@Test
	void reportsAllMissingFieldsTogether() throws Exception {
		mockMvc.perform(post("/songs").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.details.length()").value(6))
			.andExpect(jsonPath("$.errorCode").value("400"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "1900", "2099" })
	void acceptsYearAndTextLengthBoundariesWithoutTrimming(String year) throws Exception {
		Map<String, Object> body = metadata();
		body.put("name", "a");
		body.put("artist", "a".repeat(100));
		body.put("album", " Original album ");
		body.put("duration", "99:59");
		body.put("year", year);
		String payload = json.writeValueAsString(body);

		mockMvc.perform(post("/songs").contentType(MediaType.APPLICATION_JSON).content(payload))
			.andExpect(status().isOk());
		mockMvc.perform(get("/songs/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().json(payload));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "{" })
	void rejectsMissingOrMalformedRequestBody(String body) throws Exception {
		mockMvc.perform(post("/songs").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("400"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "1.5", "1.0", "\"abc\"", "2147483648" })
	void rejectsInvalidJsonIdWithoutSilentlyTruncatingIt(String invalidId) throws Exception {
		String payload = json.writeValueAsString(metadata()).replace("\"id\":" + id, "\"id\":" + invalidId);

		mockMvc.perform(post("/songs").contentType(MediaType.APPLICATION_JSON).content(payload))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("400"));

		assertFalse(repository.existsById(id));
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "1.5", "-1", "0", "2147483648" })
	void rejectsInvalidPathIds(String invalidId) throws Exception {
		mockMvc.perform(get("/songs/{id}", invalidId))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorMessage").value(
				"Invalid value '" + invalidId + "' for ID. Must be a positive integer"))
			.andExpect(jsonPath("$.details").doesNotExist())
			.andExpect(jsonPath("$.errorCode").value("400"));
	}

	@Test
	void returnsNotFoundForMissingMetadata() throws Exception {
		mockMvc.perform(get("/songs/{id}", id))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
				{"errorMessage":"Song metadata for ID=%d not found","errorCode":"404"}
				""".formatted(id)));
	}

	@Test
	void deletesOnlyExistingMetadataAndIgnoresDuplicatesAndMissingIds() throws Exception {
		repository.saveAndFlush(new Song(id, "Song", "Artist", "Album", "00:00", "1900"));

		mockMvc.perform(delete("/songs").param("id", id + "," + (id + 1) + "," + id))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"ids\":[" + id + "]}"));

		mockMvc.perform(get("/songs/{id}", id)).andExpect(status().isNotFound());
		mockMvc.perform(delete("/songs").param("id", Long.toString(id)))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"ids\":[]}"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "abc", "1.5", "0", "-1", "1,,2", ",1", "1,", "1, 2", "2147483648" })
	void rejectsMalformedCsv(String csv) throws Exception {
		mockMvc.perform(delete("/songs").param("id", csv))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorMessage").isNotEmpty())
			.andExpect(jsonPath("$.details").doesNotExist())
			.andExpect(jsonPath("$.errorCode").value("400"));
	}

	@Test
	void validatesWholeDeleteRequestBeforeDeleting() throws Exception {
		repository.saveAndFlush(new Song(id, "Song", "Artist", "Album", "00:00", "1900"));

		mockMvc.perform(delete("/songs").param("id", id + ",0"))
			.andExpect(status().isBadRequest());

		org.junit.jupiter.api.Assertions.assertTrue(repository.existsById(id));
	}

	@Test
	void rejectsMissingIdParameter() throws Exception {
		mockMvc.perform(delete("/songs"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
				{"errorMessage":"Validation error","details":{"id":"Required parameter is missing"},"errorCode":"400"}
				"""));
	}

	@Test
	void acceptsCsvOfExactly200Characters() throws Exception {
		repository.saveAndFlush(new Song(id, "Song", "Artist", "Album", "00:00", "1900"));
		String csv = "000" + id + ("," + id).repeat(17);
		assertEquals(200, csv.length());

		mockMvc.perform(delete("/songs").param("id", csv))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"ids\":[" + id + "]}"));
	}

	@Test
	void rejectsCsvOver200Characters() throws Exception {
		mockMvc.perform(delete("/songs").param("id", "1,".repeat(100) + "1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorMessage").value(
				"CSV string is too long: received 201 characters, maximum allowed is 200"));
	}
}
