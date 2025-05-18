package com.abhinavmehta.confx.sdk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDto {
    private Long id;
    private Integer priority;
    private String conditionExpression;
    private String valueToServe;
    private String description;
    // Timestamps usually not needed for SDK evaluation logic
    // private Long createdAt;
    // private Long updatedAt;
} 