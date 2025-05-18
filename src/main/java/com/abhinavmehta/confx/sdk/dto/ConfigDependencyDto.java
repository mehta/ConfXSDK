package com.abhinavmehta.confx.sdk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigDependencyDto {
    private Long id;
    private Long dependentConfigItemId;
    private String dependentConfigKey;

    private Long prerequisiteConfigItemId;
    private String prerequisiteConfigKey;
    private ConfigDataType prerequisiteDataType;
    private String prerequisiteExpectedValue;
    // Description, createdAt, updatedAt are optional for SDK evaluation logic
} 