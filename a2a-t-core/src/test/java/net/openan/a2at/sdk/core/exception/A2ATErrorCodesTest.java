package net.openan.a2at.sdk.core.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link A2ATErrorCodes}.
 *
 * <p>Tests cover the following scenarios:
 *
 * <ul>
 *   <li>The exact set of error code constants exposed by the registry
 *   <li>The exact set of validation error code constants exposed by the registry
 * </ul>
 *
 * @since 2026-06
 */
class A2ATErrorCodesTest {

    /**
     * Verifies that {@link A2ATErrorCodes} declares exactly the twenty expected error code constants with the expected
     * values.
     *
     * <p>Scenario: Reflection inspects all declared static final String fields of the registry. Expected result: The
     * field set contains exactly the twenty known constants and no others.
     *
     * @throws IllegalAccessException if a declared field cannot be read
     */
    @Test
    void should_declareExactlyTwentyConstants_When_reflectingOverDeclaredFields() throws IllegalAccessException {
        Map<String, String> constants = new TreeMap<>();
        for (Field field : A2ATErrorCodes.class.getDeclaredFields()) {
            if (field.getType() == String.class
                    && Modifier.isStatic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())) {
                field.setAccessible(true);
                constants.put(field.getName(), (String) field.get(null));
            }
        }

        assertEquals(
                Map.ofEntries(
                        Map.entry("SDK_INTERNAL_ERROR", "sdk_internal_error"),
                        Map.entry("PARAM_EXTRACTION_FAILED", "param_extraction_failed"),
                        Map.entry("TEMPLATE_NOT_FOUND", "template_not_found"),
                        Map.entry("NEGOTIATION_CONTENT_EXTRACT_FAILED", "negotiation_content_extract_failed"),
                        Map.entry("NEGOTIATION_SEMANTIC_REJECTED", "negotiation_semantic_rejected"),
                        Map.entry("NEGOTIATION_RULE_VIOLATION", "negotiation_rule_violation"),
                        Map.entry("NEGOTIATION_SLOT_MISSING", "negotiation_slot_missing"),
                        Map.entry("NEGOTIATION_INVALID_INPUT", "negotiation_invalid_input"),
                        Map.entry("NEGOTIATION_LLM_INFRASTRUCTURE_ERROR", "negotiation_llm_infrastructure_error"),
                        Map.entry("VALIDATION_INVALID_INPUT", "validation_invalid_input"),
                        Map.entry("VALIDATION_RULE_VIOLATION", "validation_rule_violation"),
                        Map.entry("VALIDATION_SEMANTIC_REJECTED", "validation_semantic_rejected"),
                        Map.entry("VALIDATION_LLM_INFRASTRUCTURE_ERROR", "validation_llm_infrastructure_error"),
                        Map.entry("VALIDATION_PROMPT_RESOURCE_NOT_FOUND", "validation_prompt_resource_not_found"),
                        Map.entry("PROMPT_RESOURCE_LOAD_ERROR", "prompt_resource_load_error"),
                        Map.entry("SLOT_SCHEMA_NOT_FOUND", "slot_schema_not_found"),
                        Map.entry("LLM_INVOCATION_FAILED", "llm_invocation_failed"),
                        Map.entry("RENDER_FAILED", "render_failed"),
                        Map.entry("SLOT_VALIDATION_ERROR", "slot_validation_error"),
                        Map.entry("PROCESSED_PROMPT_PARSE_ERROR", "processed_prompt_parse_error")),
                constants);
    }
}
