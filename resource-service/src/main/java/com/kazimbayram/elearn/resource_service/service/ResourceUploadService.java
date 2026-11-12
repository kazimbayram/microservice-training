package com.kazimbayram.elearn.resource_service.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.kazimbayram.elearn.common.web.exception.BadRequestException;
import com.kazimbayram.elearn.resource_service.client.SongApiClient;
import com.kazimbayram.elearn.resource_service.entity.Resource;
import com.kazimbayram.elearn.resource_service.mapper.SongMetadataMapper;
import com.kazimbayram.elearn.resource_service.metadata.AudioMetadataExtractor;
import com.kazimbayram.elearn.resource_service.repository.ResourceRepository;

import org.apache.tika.exception.TikaException;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.SAXException;

@Service
public class ResourceUploadService {

	private static final MediaType MP3 = MediaType.parseMediaType("audio/mpeg");

	private final ResourceRepository repository;
	private final AudioMetadataExtractor extractor;
	private final SongMetadataMapper mapper;
	private final SongApiClient songClient;

	public ResourceUploadService(ResourceRepository repository, AudioMetadataExtractor extractor,
			SongMetadataMapper mapper, SongApiClient songClient) {
		this.repository = repository;
		this.extractor = extractor;
		this.mapper = mapper;
		this.songClient = songClient;
	}

	@Transactional
	public Long upload(String contentType, byte[] data) {
		validateContentType(contentType);
		if (data == null || data.length == 0) {
			throw new BadRequestException("Invalid MP3 file: request body must not be empty");
		}
		Map<String, List<String>> tags;
		try (ByteArrayInputStream input = new ByteArrayInputStream(data)) {
			tags = extractor.extract(input);
		}
		catch (IOException | SAXException | TikaException exception) {
			throw new BadRequestException("Invalid MP3 file", exception);
		}
		Resource resource = repository.saveAndFlush(new Resource("resource.mp3", "audio/mpeg", data));
		songClient.create(mapper.toRequest(resource.getId(), tags));
		return resource.getId();
	}

	private void validateContentType(String contentType) {
		try {
			if (contentType != null && MP3.equalsTypeAndSubtype(MediaType.parseMediaType(contentType))) {
				return;
			}
		}
		catch (InvalidMediaTypeException exception) {
			throw invalidFormat(contentType);
		}
		throw invalidFormat(contentType);
	}

	private BadRequestException invalidFormat(String contentType) {
		return new BadRequestException("Invalid file format: " + (contentType == null ? "missing" : contentType)
			+ ". Only MP3 files are allowed");
	}
}
