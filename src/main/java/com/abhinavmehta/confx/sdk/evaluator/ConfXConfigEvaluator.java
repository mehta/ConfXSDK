package com.abhinavmehta.confx.sdk.evaluator;

import com.abhinavmehta.confx.sdk.dto.ConfigDataType;
import com.abhinavmehta.confx.sdk.dto.ConfigVersionDto;
import com.abhinavmehta.confx.sdk.dto.EvaluationContext;
import com.abhinavmehta.confx.sdk.dto.RuleDto;
import com.abhinavmehta.confx.sdk.store.ConfigCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ConfXConfigEvaluator {
    private static final Logger log = LoggerFactory.getLogger(ConfXConfigEvaluator.class);
    private final ConfigCache configCache;
    private final ConfXRuleEvaluator ruleEvaluator;
    private final ObjectMapper objectMapper; // For JSON parsing and comparison
    // SDK needs to know about its own project ID and environment ID to fetch dependencies correctly, 
    // as dependency definitions are not environment specific, but their evaluation is.
    private final Long sdkProjectId;
    private final Long sdkEnvironmentId;


    public ConfXConfigEvaluator(ConfigCache configCache, Long projectId, Long environmentId, ObjectMapper objectMapper) {
        this.configCache = configCache;
        this.ruleEvaluator = new ConfXRuleEvaluator();
        this.sdkProjectId = projectId;
        this.sdkEnvironmentId = environmentId;
        this.objectMapper = objectMapper;
    }

    public EvaluatedConfigResult evaluate(String configKey, EvaluationContext evalContext) {
        return evaluateInternal(configKey, evalContext, new HashSet<>());
    }

    private EvaluatedConfigResult evaluateInternal(String configKey, EvaluationContext evalContext, Set<String> evaluationStack) {
        Optional<ConfigVersionDto> configVersionOpt = configCache.getConfig(configKey);
        if (configVersionOpt.isEmpty()) {
            log.debug("SDK: Config key '{}' not found in cache.", configKey);
            return new EvaluatedConfigResult(null, null, null, "NOT_FOUND", null, null);
        }
        ConfigVersionDto configVersion = configVersionOpt.get();

        if (evaluationStack.contains(configKey)) {
            log.warn("SDK: Cyclic dependency detected for configKey: {}. Stack: {}", configKey, evaluationStack);
            Object offValue = getOffValue(configVersion.getConfigItemDataType());
            return new EvaluatedConfigResult(offValue, configVersion.getConfigItemDataType(), null, "CYCLIC_DEPENDENCY_ERROR", configVersion.getId(), configVersion.getVersionNumber());
        }
        evaluationStack.add(configKey);

        // Check prerequisites (Dependencies are stored on ConfigItem level, not version specific in current model)
        // For SDK, we assume dependencies are defined on ConfigItem and fetched/available somehow if needed.
        // The current server-side ConfigDependency is linked to ConfigItem. The SDK cache has ConfigVersionDto.
        // This part needs careful thought: How does SDK know about dependencies of a configKey?
        // For now, let's assume dependencies are NOT YET handled in this client-side evaluator fully without server support
        // to provide dependency info with ConfigVersionDto or via a separate SDK mechanism.
        // As a simplification, we'll skip dependency evaluation in the SDK for now, focusing on rules.
        // TODO: Integrate full dependency evaluation if SDK is to be fully standalone for this.

        // Rule Evaluation
        ConfXRuleEvaluator.MatchedRuleResult ruleResult = ruleEvaluator.evaluateRules(configVersion.getRules(), evalContext);
        String resolvedValueString;
        Long matchedRuleId = null;
        String evaluationSource;

        if (ruleResult.hasMatch()) {
            resolvedValueString = ruleResult.getValueToServe();
            matchedRuleId = ruleResult.getMatchedRuleId();
            evaluationSource = "RULE_MATCH";
        } else {
            resolvedValueString = configVersion.getValue(); // Default value from the config version
            evaluationSource = "DEFAULT_VALUE";
        }

        Object typedValue = convertToActualType(resolvedValueString, configVersion.getConfigItemDataType());
        evaluationStack.remove(configKey);
        
        return new EvaluatedConfigResult(typedValue, configVersion.getConfigItemDataType(), matchedRuleId, evaluationSource, configVersion.getId(), configVersion.getVersionNumber());
    }

    private Object getOffValue(ConfigDataType dataType) {
        return dataType == ConfigDataType.BOOLEAN ? false : null;
    }

    private Object convertToActualType(String stringValue, ConfigDataType dataType) {
        if (stringValue == null) {
            return (dataType == ConfigDataType.BOOLEAN) ? false : null;
        }
        try {
            switch (dataType) {
                case BOOLEAN: return Boolean.parseBoolean(stringValue);
                case INTEGER: return Integer.parseInt(stringValue);
                case DOUBLE:  return Double.parseDouble(stringValue);
                case STRING:  return stringValue;
                case JSON:
                    return objectMapper.readTree(stringValue);
                default:
                    log.warn("SDK: Unsupported data type for conversion: {}", dataType);
                    return stringValue;
            }
        } catch (Exception e) {
            log.error("SDK: Failed to convert value '{}' to type {}: {}", stringValue, dataType, e.getMessage());
            // For SDK, returning a default or null might be better than throwing an exception that breaks the app.
            return getOffValue(dataType); // Return default 'off' value on conversion error
        }
    }

    // Placeholder for results, could be more elaborate
    public static class EvaluatedConfigResult {
        public final Object value;
        public final ConfigDataType dataType;
        public final Long matchedRuleId;
        public final String evaluationSource;
        public final Long versionId;
        public final Integer versionNumber;

        public EvaluatedConfigResult(Object value, ConfigDataType dataType, Long matchedRuleId, String source, Long vId, Integer vNum) {
            this.value = value;
            this.dataType = dataType;
            this.matchedRuleId = matchedRuleId;
            this.evaluationSource = source;
            this.versionId = vId;
            this.versionNumber = vNum;
        }
    }
} 