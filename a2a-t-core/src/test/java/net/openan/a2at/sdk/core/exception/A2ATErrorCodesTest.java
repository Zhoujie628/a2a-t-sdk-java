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
 * </ul>
 *
 * @since 2026-06
 */
class A2ATErrorCodesTest {

    /**
     * Verifies that {@link A2ATErrorCodes} declares exactly the eight expected error code constants with the expected
     * values.
     *
     * <p>Scenario: Reflection inspects all declared static final String fields of the registry. Expected result: The
     * field set contains exactly the eight known constants and no others.
     *
     * @throws IllegalAccessException if a declared field cannot be read
     */
    @Test
    void should_declareExactlyEightConstants_When_reflectingOverDeclaredFields() throws IllegalAccessException {
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
                Map.of(
                        "PARAM_EXTRACTION_FAILED", "param_extraction_failed",
                        "TEMPLATE_NOT_FOUND", "template_not_found",
                        "NEGOTIATION_CONTENT_EXTRACT_FAILED", "negotiation_content_extract_failed",
                        "NEGOTIATION_SEMANTIC_REJECTED", "negotiation_semantic_rejected",
                        "NEGOTIATION_RULE_VIOLATION", "negotiation_rule_violation",
                        "NEGOTIATION_SLOT_MISSING", "negotiation_slot_missing",
                        "NEGOTIATION_INVALID_INPUT", "negotiation_invalid_input",
                        "NEGOTIATION_LLM_INFRASTRUCTURE_ERROR", "negotiation_llm_infrastructure_error"),
                constants);
    }
}
