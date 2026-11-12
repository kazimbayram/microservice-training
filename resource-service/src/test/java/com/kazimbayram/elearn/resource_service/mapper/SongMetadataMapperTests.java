package com.kazimbayram.elearn.resource_service.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;
import org.junit.jupiter.api.Test;

class SongMetadataMapperTests {

	private final SongMetadataMapper mapper = new SongMetadataMapper();

	@Test
	void doesNotNormalizeTruncateOrReplaceExtractedValues() {
		var metadata = mapper.toRequest(7L, Map.of(
			TikaCoreProperties.TITLE.getName(), List.of("  Title  "),
			XMPDM.ARTIST.getName(), List.of("Artist"),
			XMPDM.ALBUM.getName(), List.of("a".repeat(101)),
			XMPDM.DURATION.getName(), List.of("02:05"),
			XMPDM.RELEASE_DATE.getName(), List.of("1977-10-28")));

		assertEquals("  Title  ", metadata.name());
		assertEquals("a".repeat(101), metadata.album());
		assertEquals("1977-10-28", metadata.year());
		assertEquals("02:05", metadata.duration());
	}

	@Test
	void doesNotInventMissingTags() {
		var metadata = mapper.toRequest(7L, Map.of());

		assertEquals(7L, metadata.id());
		assertNull(metadata.name());
		assertNull(metadata.artist());
		assertNull(metadata.album());
		assertNull(metadata.duration());
		assertNull(metadata.year());
	}
}
