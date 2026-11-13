package com.kazimbayram.elearn.resource_service.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class SongClientConfiguration {

	@Bean
	@Primary
	@Scope("prototype")
	RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}

	@Bean
	@LoadBalanced
	@Scope("prototype")
	RestClient.Builder songRestClientBuilder() {
		return RestClient.builder();
	}
}
