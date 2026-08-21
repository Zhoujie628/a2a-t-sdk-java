package net.openan.a2at.sdk.negotiation.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import org.junit.jupiter.api.Test;

/**
 * Guards the delivered negotiation resources and the error-code constant registry.
 *
 * <p>The twelve templates resolved from the test classpath must be byte-identical to the delivered template files of
 * the working tree, so that no packaging or formatting step can silently rewrite the authoritative template bytes. The
 * error-code constants must stay exactly the pinned eight-constant registry.
 */
class NegotiationDeliveredResourceContractTest {

    private static final List<NegotiationType> TYPE_ORDER =
            List.of(NegotiationType.INFORMATION, NegotiationType.TARGET, NegotiationType.FEASIBILITY);

    private static final List<NegotiationPhase> PHASE_ORDER =
            List.of(NegotiationPhase.PROPOSE, NegotiationPhase.ACCEPT);

    private static final List<String> LANGUAGES = List.of("zh-CN", "en-US");

    @Test
    void everyPackagedTemplateIsByteIdenticalToTheDeliveredFile() throws IOException {
        List<String> mismatches = new ArrayList<>();
        int compared = 0;
        for (NegotiationType type : TYPE_ORDER) {
            for (NegotiationPhase phase : PHASE_ORDER) {
                for (String language : LANGUAGES) {
                    String relativePath = String.join(
                            "/",
                            "templates",
                            "Negotiation-T",
                            "v1",
                            type.typeSegment(),
                            phase.uriSegment(),
                            language,
                            "template.md");
                    byte[] packaged = readClasspathBytes("prompt_resources/" + relativePath);
                    byte[] delivered =
                            Files.readAllBytes(deliveredResourceRoot().resolve(relativePath));
                    compared++;
                    if (!java.util.Arrays.equals(packaged, delivered)) {
                        mismatches.add(relativePath);
                    }
                }
            }
        }
        assertEquals(12, compared, "exactly the twelve negotiation templates must be compared");
        assertTrue(
                mismatches.isEmpty(), "packaged templates must match the delivered files byte for byte: " + mismatches);
    }

    @Test
    void errorCodeConstantsStayExactlyThePinnedRegistry() {
        java.util.Map<String, String> expected = new java.util.LinkedHashMap<>();
        expected.put("SDK_INTERNAL_ERROR", "sdk_internal_error");
        expected.put("PARAM_EXTRACTION_FAILED", "param_extraction_failed");
        expected.put("TEMPLATE_NOT_FOUND", "template_not_found");
        expected.put("NEGOTIATION_CONTENT_EXTRACT_FAILED", "negotiation_content_extract_failed");
        expected.put("NEGOTIATION_SEMANTIC_REJECTED", "negotiation_semantic_rejected");
        expected.put("NEGOTIATION_RULE_VIOLATION", "negotiation_rule_violation");
        expected.put("NEGOTIATION_SLOT_MISSING", "negotiation_slot_missing");
        expected.put("NEGOTIATION_INVALID_INPUT", "negotiation_invalid_input");
        expected.put("NEGOTIATION_LLM_INFRASTRUCTURE_ERROR", "negotiation_llm_infrastructure_error");
        expected.put("VALIDATION_INVALID_INPUT", "validation_invalid_input");
        expected.put("VALIDATION_RULE_VIOLATION", "validation_rule_violation");
        expected.put("VALIDATION_SEMANTIC_REJECTED", "validation_semantic_rejected");
        expected.put("VALIDATION_LLM_INFRASTRUCTURE_ERROR", "validation_llm_infrastructure_error");
        expected.put("VALIDATION_PROMPT_RESOURCE_NOT_FOUND", "validation_prompt_resource_not_found");

        java.util.Map<String, String> actual = new java.util.LinkedHashMap<>();
        for (java.lang.reflect.Field field : A2ATErrorCodes.class.getDeclaredFields()) {
            if (field.getType() == String.class && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                try {
                    actual.put(field.getName(), (String) field.get(null));
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException("Failed to read the constant " + field.getName(), exception);
                }
            }
        }

        assertEquals(
                expected,
                actual,
                "the error-code registry is locked; adding or changing codes must update this" + " test");
        assertTrue(
                !actual.containsValue("negotiation_type_unrecognized"),
                "the removed type-recognition code must not reappear");
    }

    private static byte[] readClasspathBytes(String classpathPath) throws IOException {
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathPath);
        assertNotNull(stream, "the resource must exist on the test classpath: " + classpathPath);
        try (stream) {
            return stream.readAllBytes();
        }
    }

    private static Path deliveredResourceRoot() {
        return Path.of("..", "a2a-t-resources", "src", "main", "resources", "prompt_resources");
    }
}
