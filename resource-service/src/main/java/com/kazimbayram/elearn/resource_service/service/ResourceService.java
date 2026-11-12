package com.kazimbayram.elearn.resource_service.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import com.kazimbayram.elearn.resource_service.entity.Resource;
import com.kazimbayram.elearn.resource_service.client.SongApiClient;
import com.kazimbayram.elearn.resource_service.exception.ResourceNotFoundException;
import com.kazimbayram.elearn.common.web.validation.PositiveId;
import com.kazimbayram.elearn.common.web.validation.IdCsv;
import com.kazimbayram.elearn.resource_service.repository.ResourceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class ResourceService {

	private final ResourceRepository resourceRepository;
	private final SongApiClient songClient;

	public ResourceService(ResourceRepository resourceRepository, SongApiClient songClient) {
		this.resourceRepository = resourceRepository;
		this.songClient = songClient;
	}

	public Resource save(String fileName, String contentType, byte[] data) {
		return resourceRepository.save(new Resource(fileName, contentType, data));
	}

	@Transactional(readOnly = true)
	public Resource get(Long id) {
		return resourceRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException(id));
	}

	@Transactional(readOnly = true)
	public byte[] getAudio(@PositiveId String id) {
		return get(Long.valueOf(id)).getData();
	}

	@Transactional(readOnly = true)
	public List<Resource> getAll() {
		return resourceRepository.findAll();
	}

	public void delete(Long id) {
		if (!resourceRepository.existsById(id)) {
			throw new ResourceNotFoundException(id);
		}
		resourceRepository.deleteById(id);
		resourceRepository.flush();
		songClient.deleteAll(List.of(id));
	}

	public List<Long> deleteAll(@IdCsv String csv) {
		List<Long> ids = Arrays.stream(csv.split(",")).map(Long::valueOf).toList();
		List<Long> deletedIds = new ArrayList<>();
		for (Long id : new LinkedHashSet<>(ids)) {
			resourceRepository.findById(id).ifPresent(resource -> {
				resourceRepository.delete(resource);
				deletedIds.add(id);
			});
		}
		if (!deletedIds.isEmpty()) {
			resourceRepository.flush();
			songClient.deleteAll(deletedIds);
		}
		return deletedIds;
	}
}
