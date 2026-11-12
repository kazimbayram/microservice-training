package com.kazimbayram.elearn.resource_service.repository;

import com.kazimbayram.elearn.resource_service.entity.Resource;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
}
