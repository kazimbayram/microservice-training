package com.kazimbayram.elearn.resource_service.client;

import java.util.List;
import java.util.stream.Collectors;

import com.kazimbayram.elearn.resource_service.dto.SongMetadataRequest;
import com.kazimbayram.elearn.resource_service.exception.SongApiException;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class SongApiClient {

	private final RestClient restClient;

	public SongApiClient(@LoadBalanced RestClient.Builder builder, ObjectMapper objectMapper) {
		restClient = builder.baseUrl("http://song-service")
			.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
				SongApiError error;
				try {
					error = objectMapper.readValue(response.getBody(), SongApiError.class);
				}
				catch (JacksonException exception) {
					throw new RestClientException("Song Service returned " + response.getStatusCode().value()
						+ " with an invalid JSON error response", exception);
				}
				if (error == null || error.errorMessage() == null || error.errorCode() == null
						|| !Integer.toString(response.getStatusCode().value()).equals(error.errorCode())) {
					throw new RestClientException("Song Service returned " + response.getStatusCode().value()
						+ " with an invalid error response structure");
				}
				throw new SongApiException(response.getStatusCode(), error);
			})
			.build();
	}

	public void deleteAll(List<Long> ids) {
		if (ids.isEmpty()) {
			return;
		}
		String csv = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
		restClient.delete().uri(builder -> builder.path("/songs").queryParam("id", csv).build())
			.retrieve()
			.toBodilessEntity();
	}

	public void create(SongMetadataRequest metadata) {
		SongCreatedResponse response = restClient.post().uri("/songs")
			.contentType(MediaType.APPLICATION_JSON)
			.body(metadata)
			.retrieve()
			.body(SongCreatedResponse.class);
		if (response == null || !metadata.id().equals(response.id())) {
			throw new IllegalStateException("Song Service returned an unexpected metadata ID");
		}
	}

	public record SongCreatedResponse(Long id) {
	}
}
