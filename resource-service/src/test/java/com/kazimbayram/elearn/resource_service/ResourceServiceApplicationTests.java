package com.kazimbayram.elearn.resource_service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kazimbayram.elearn.resource_service.entity.Resource;
import com.kazimbayram.elearn.resource_service.repository.ResourceRepository;
import com.kazimbayram.elearn.resource_service.support.Mp3Fixture;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class ResourceServiceApplicationTests {

	@Autowired
	private ResourceRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void persistsAndReloadsAudio() {
		byte[] audio = Mp3Fixture.audio();
		Resource saved = repository.saveAndFlush(new Resource("test.mp3", "audio/mpeg", audio));
		entityManager.clear();

		Resource loaded = repository.findById(saved.getId()).orElseThrow();
		assertEquals("test.mp3", loaded.getFileName());
		assertEquals("audio/mpeg", loaded.getContentType());
		assertArrayEquals(audio, loaded.getData());
	}

}
