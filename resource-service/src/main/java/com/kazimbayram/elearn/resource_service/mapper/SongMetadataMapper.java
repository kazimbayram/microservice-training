package com.kazimbayram.elearn.resource_service.mapper;

import java.util.List;
import java.util.Map;

import com.kazimbayram.elearn.resource_service.dto.SongMetadataRequest;

import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;
import org.springframework.stereotype.Component;

@Component
public class SongMetadataMapper {

	public SongMetadataRequest toRequest(Long id, Map<String, List<String>> tags) {
		return new SongMetadataRequest(id, value(tags, TikaCoreProperties.TITLE.getName()),
			value(tags, XMPDM.ARTIST.getName()), value(tags, XMPDM.ALBUM.getName()),
			value(tags, XMPDM.DURATION.getName()), value(tags, XMPDM.RELEASE_DATE.getName()));
	}

	private String value(Map<String, List<String>> tags, String key) {
		List<String> values = tags.get(key);
		return values == null || values.isEmpty() ? null : values.getFirst();
	}
}
