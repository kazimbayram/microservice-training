package com.kazimbayram.elearn.resource_service.client;

import java.util.Map;

public record SongApiError(String errorMessage, String errorCode, Map<String, String> details) {
}
