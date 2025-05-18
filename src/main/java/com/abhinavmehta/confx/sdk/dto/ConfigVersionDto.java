package com.abhinavmehta.confx.sdk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Good practice for DTOs from external sources
public class ConfigVersionDto {
    private Long id; // Version ID
    private Long configItemId;
    private String configItemKey;
    private ConfigDataType configItemDataType;
    private Long environmentId;
    // private String environmentName; // SDK primarily cares about its configured environmentId
    private String value; // Default value for this version
    private boolean isActive; // Should always be true for configs fetched by SDK unless we fetch history
    private Integer versionNumber;
    // private String changeDescription; // Potentially useful for debugging, but not core to evaluation
    private List<RuleDto> rules;
    // Timestamps (createdAt, updatedAt) generally not needed for SDK evaluation logic
} 