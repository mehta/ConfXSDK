package com.abhinavmehta.confx.sdk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * Represents the generic structure of the data field in an SSE event from ConfX server.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SseEventDto {
    private String type;
    private JsonNode payload; // Keep as JsonNode for flexible parsing based on type
} 