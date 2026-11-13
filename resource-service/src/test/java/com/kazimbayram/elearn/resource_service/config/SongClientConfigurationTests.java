package com.kazimbayram.elearn.resource_service.config;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

class SongClientConfigurationTests {

	@Test
	void keepsInfrastructureClientSeparateFromLoadBalancedSongClient() {
		try (var context = new AnnotationConfigApplicationContext(SongClientConfiguration.class)) {
			RestClient.Builder infrastructure = context.getBean(RestClient.Builder.class);
			RestClient.Builder song = context.getBean("songRestClientBuilder", RestClient.Builder.class);

			assertNotSame(infrastructure, song);
			assertNotSame(infrastructure, context.getBean(RestClient.Builder.class));
			assertNotSame(song, context.getBean("songRestClientBuilder"));
			assertTrue(context.findAnnotationOnBean("songRestClientBuilder", LoadBalanced.class) != null);
			assertTrue(context.findAnnotationOnBean("restClientBuilder", LoadBalanced.class) == null);
		}
	}
}
