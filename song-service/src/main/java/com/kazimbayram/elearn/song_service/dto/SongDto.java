package com.kazimbayram.elearn.song_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SongDto(
		@NotNull(message = "ID is required")
		@Positive(message = "ID must be positive")
		@Max(value = Integer.MAX_VALUE, message = "ID must not exceed 2147483647")
		Long id,
		@NotNull(message = "Song name is required")
		@Pattern(regexp = "(?s).*\\S.*", message = "Song name must be between 1 and 100 characters")
		@Size(min = 1, max = 100, message = "Song name must be between 1 and 100 characters")
		String name,
		@NotNull(message = "Artist name is required")
		@Pattern(regexp = "(?s).*\\S.*", message = "Artist name must be between 1 and 100 characters")
		@Size(min = 1, max = 100, message = "Artist name must be between 1 and 100 characters")
		String artist,
		@NotNull(message = "Album name is required")
		@Pattern(regexp = "(?s).*\\S.*", message = "Album name must be between 1 and 100 characters")
		@Size(min = 1, max = 100, message = "Album name must be between 1 and 100 characters")
		String album,
		@NotNull(message = "Duration is required")
		@Pattern(regexp = "[0-9]{2}:[0-5][0-9]",
			message = "Duration must be in mm:ss format with leading zeros")
		String duration,
		@NotNull(message = "Year is required")
		@Pattern(regexp = "(19|20)[0-9]{2}", message = "Year must be between 1900 and 2099")
		String year) {
}
