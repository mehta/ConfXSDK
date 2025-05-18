package com.abhinavmehta.confx.sdk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationContext {
    @Builder.Default
    private Map<String, Object> attributes = Collections.emptyMap();
} 