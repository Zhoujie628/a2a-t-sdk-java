package net.openan.a2at.sdk.core.validation;

import java.util.Map;

/**
 * Deterministic rule-level checker for content.
 *
 * <p>The checker is deterministic and never calls an LLM. It validates structural constraints of the content and
 * returns context parameters parsed from it.
 *
 * @since 2026-08
 */
public interface RuleChecker {

    /**
     * Runs the rule-level check of one content prompt.
     *
     * @param prompt content prompt text
     * @return context parameters parsed from the content
     * @throws ContentValidationException if the content violates a structural rule
     */
    Map<String, Object> check(String prompt);
}