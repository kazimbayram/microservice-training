package com.kazimbayram.elearn.resource_service.dto;

public record SongMetadataRequest(Long id, String name, String artist, String album, String duration, String year) {
}
