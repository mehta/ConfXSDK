package com.abhinavmehta.confx.sdk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeletedConfigItemPayload {
    private Long configItemId;
    private String configKey;
} 