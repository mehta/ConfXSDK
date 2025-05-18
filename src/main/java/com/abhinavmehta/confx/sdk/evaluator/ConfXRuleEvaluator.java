package com.abhinavmehta.confx.sdk.evaluator;

import com.abhinavmehta.confx.sdk.dto.EvaluationContext;
import com.abhinavmehta.confx.sdk.dto.RuleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.List;
import java.util.Map;

public class ConfXRuleEvaluator {
    private static final Logger log = LoggerFactory.getLogger(ConfXRuleEvaluator.class);
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * Evaluates rules against the given context and returns the value from the first matching rule.
     * Rules are assumed to be sorted by priority (SDK user should ensure this or sort before passing).
     * @param rules The list of RuleDto to evaluate.
     * @param evalContext The evaluation context containing attributes.
     * @return An Optional containing the value from the first matching rule, or Optional.empty() if no rules match.
     *         Also returns the ID of the matched rule if one was found.
     */
    public MatchedRuleResult evaluateRules(List<RuleDto> rules, EvaluationContext evalContext) {
        if (rules == null || rules.isEmpty()) {
            return new MatchedRuleResult(null, null);
        }

        Map<String, Object> attributes = (evalContext != null && evalContext.getAttributes() != null) 
                                         ? evalContext.getAttributes() 
                                         : Map.of();
        
        StandardEvaluationContext spelContext = new StandardEvaluationContext(attributes);

        // Rules should already be sorted by priority when fetched/cached.
        for (RuleDto rule : rules) {
            try {
                Boolean matches = expressionParser.parseExpression(rule.getConditionExpression()).getValue(spelContext, Boolean.class);
                if (Boolean.TRUE.equals(matches)) {
                    log.debug("SDK Rule matched (ID {}): '{}'. Serving value: '{}'", 
                              rule.getId(), rule.getConditionExpression(), rule.getValueToServe());
                    return new MatchedRuleResult(rule.getValueToServe(), rule.getId());
                }
            } catch (Exception e) {
                log.error("SDK Error evaluating rule (ID {}): '{}'. Condition: '{}'. Error: {}", 
                          rule.getId(), rule.getDescription(), rule.getConditionExpression(), e.getMessage());
            }
        }
        return new MatchedRuleResult(null, null); // No rule matched
    }

    public static class MatchedRuleResult {
        private final String valueToServe;
        private final Long matchedRuleId;

        public MatchedRuleResult(String valueToServe, Long matchedRuleId) {
            this.valueToServe = valueToServe;
            this.matchedRuleId = matchedRuleId;
        }

        public String getValueToServe() {
            return valueToServe;
        }

        public Long getMatchedRuleId() {
            return matchedRuleId;
        }

        public boolean hasMatch() {
            return valueToServe != null;
        }
    }
} 