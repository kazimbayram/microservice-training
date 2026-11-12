package com.kazimbayram.elearn.resource_service.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import com.kazimbayram.elearn.resource_service.support.Mp3Fixture;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;
import org.junit.jupiter.api.Test;

class AudioMetadataExtractorTests {

	private final AudioMetadataExtractor extractor = new AudioMetadataExtractor();

	@Test
	void preservesExtractedTagsAndFormatsDuration() {
		Metadata metadata = new Metadata();
		metadata.add(TikaCoreProperties.TITLE, "  Original title  ");
		metadata.add(XMPDM.DURATION, "125.9");

		Map<String, List<String>> tags = extractor.toTagMap(metadata);

		assertEquals(List.of("  Original title  "), tags.get(TikaCoreProperties.TITLE.getName()));
		assertEquals(List.of("02:05"), tags.get(XMPDM.DURATION.getName()));
	}

	@Test
	void extractsTagsFromMp3Frames() throws Exception {
		Map<String, List<String>> tags = extractor.extract(new ByteArrayInputStream(Mp3Fixture.audio()));

		assertEquals(List.of("  Original title  "), tags.get(TikaCoreProperties.TITLE.getName()));
		assertEquals(List.of("Queen"), tags.get(XMPDM.ARTIST.getName()));
		assertEquals(List.of("News of the world"), tags.get(XMPDM.ALBUM.getName()));
		assertEquals(List.of("1977"), tags.get(XMPDM.RELEASE_DATE.getName()));
		assertEquals(List.of("00:02"), tags.get(XMPDM.DURATION.getName()));
	}

	@Test
	void rejectsGarbageAndTagsWithoutAudioFrames() {
		assertThrows(TikaException.class, () -> extractor.extract(new ByteArrayInputStream("not mp3".getBytes())));
		byte[] audio = Mp3Fixture.audio();
		int tagLength = ((audio[8] & 127) << 7) + (audio[9] & 127);
		byte[] tagsOnly = java.util.Arrays.copyOf(audio, 10 + tagLength);
		assertThrows(TikaException.class, () -> extractor.extract(new ByteArrayInputStream(tagsOnly)));
	}
}
