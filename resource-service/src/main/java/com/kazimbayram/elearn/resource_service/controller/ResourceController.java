package com.kazimbayram.elearn.resource_service.controller;

import com.kazimbayram.elearn.common.web.validation.PositiveId;
import com.kazimbayram.elearn.common.web.validation.IdCsv;
import com.kazimbayram.elearn.resource_service.dto.DeleteResourcesResponse;
import com.kazimbayram.elearn.resource_service.dto.CreateResourceResponse;
import com.kazimbayram.elearn.resource_service.service.ResourceService;
import com.kazimbayram.elearn.resource_service.service.ResourceUploadService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/resources")
public class ResourceController {

	private final ResourceService resourceService;
	private final ResourceUploadService uploadService;

	public ResourceController(ResourceService resourceService, ResourceUploadService uploadService) {
		this.resourceService = resourceService;
		this.uploadService = uploadService;
	}

	@PostMapping
	public ResponseEntity<CreateResourceResponse> upload(
			@RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
			@RequestBody(required = false) byte[] data) {
		return ResponseEntity.ok(new CreateResourceResponse(uploadService.upload(contentType, data)));
	}

	@GetMapping("/{id}")
	public ResponseEntity<byte[]> get(@PositiveId @PathVariable("id") String id) {
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("audio/mpeg"))
			.body(resourceService.getAudio(id));
	}

	@DeleteMapping
	public ResponseEntity<DeleteResourcesResponse> delete(@IdCsv @RequestParam("id") String ids) {
		return ResponseEntity.ok(new DeleteResourcesResponse(resourceService.deleteAll(ids)));
	}
}
