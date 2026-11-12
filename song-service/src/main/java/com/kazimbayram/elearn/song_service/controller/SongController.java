package com.kazimbayram.elearn.song_service.controller;

import com.kazimbayram.elearn.common.web.validation.PositiveId;
import com.kazimbayram.elearn.common.web.validation.IdCsv;
import com.kazimbayram.elearn.song_service.dto.CreateSongResponse;
import com.kazimbayram.elearn.song_service.dto.DeleteSongsResponse;
import com.kazimbayram.elearn.song_service.dto.SongDto;
import com.kazimbayram.elearn.song_service.service.SongService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/songs")
public class SongController {

	private final SongService service;

	public SongController(SongService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<CreateSongResponse> create(@Valid @RequestBody SongDto song) {
		return ResponseEntity.ok(new CreateSongResponse(service.create(song)));
	}

	@GetMapping("/{id}")
	public ResponseEntity<SongDto> get(@PositiveId @PathVariable("id") String id) {
		return ResponseEntity.ok(service.get(id));
	}

	@DeleteMapping
	public ResponseEntity<DeleteSongsResponse> delete(@IdCsv @RequestParam("id") String ids) {
		return ResponseEntity.ok(new DeleteSongsResponse(service.deleteAll(ids)));
	}
}
