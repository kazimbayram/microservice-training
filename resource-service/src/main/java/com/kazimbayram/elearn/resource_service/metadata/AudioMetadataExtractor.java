package com.kazimbayram.elearn.resource_service.metadata;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.mp3.Mp3Parser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.helpers.DefaultHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

@Component
public class AudioMetadataExtractor {

	private static final String DURATION_KEY = XMPDM.DURATION.getName();

	public Map<String, List<String>> extract(InputStream inputStream)
			throws IOException, SAXException, TikaException {
		Metadata metadata = new Metadata();
		new Mp3Parser().parse(inputStream, new DefaultHandler(), metadata, new ParseContext());
		if (metadata.get("samplerate") == null || metadata.get(XMPDM.DURATION) == null) {
			throw new TikaException("No MPEG audio frames found");
		}
		return toTagMap(metadata);
	}

	Map<String, List<String>> toTagMap(Metadata metadata) {
		Map<String, List<String>> tags = new LinkedHashMap<>();
		for (String name : metadata.names()) {
			String[] values = metadata.getValues(name);
			List<String> tagValues = new ArrayList<>(values.length);
			for (String value : values) {
				tagValues.add(DURATION_KEY.equals(name) ? formatDuration(value) : value);
			}
			tags.put(name, List.copyOf(tagValues));
		}
		return Collections.unmodifiableMap(tags);
	}

	private String formatDuration(String secondsValue) {
		long totalSeconds;
		try {
			totalSeconds = new BigDecimal(secondsValue).longValue();
		}
		catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Invalid audio duration: " + secondsValue, exception);
		}
		if (totalSeconds < 0) {
			throw new IllegalArgumentException("Audio duration cannot be negative: " + secondsValue);
		}
		return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60, totalSeconds % 60);
	}
}
