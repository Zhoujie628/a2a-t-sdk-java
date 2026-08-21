package net.openan.a2at.sdk.negotiation.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.validation.StandardTemplates;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import org.junit.jupiter.api.Test;

class DefaultNegotiationSemanticValidatorTest {

    private static final String VALID_PROMPT = "## Negotiation Context\n"
            + "- id: 3dbc13b5-bd57-4c2b-b503-24e381b6c8d3\n"
            + "- round: 1\n"
            + "- maxRounds: 5\n\n"
            + "## Required Information Items\n"
            + "1. energy saving region: provide a real region\n";

    private final RecordingSchemaBuilder schemaBuilder = new RecordingSchemaBuilder();

    private final RecordingLLMClient llmClient = new RecordingLLMClient();

    private final DefaultNegotiationSemanticValidator validator =
            new DefaultNegotiationSemanticValidator(llmClient, schemaBuilder);

    private final NegotiationReference informationReference =
            new NegotiationReference(NegotiationType.INFORMATION, NegotiationPhase.PROPOSE, "en-US");

    private final Map<String, Object> callerSchema =
            Map.of("type", "object", "properties", Map.of("confirmed_rate_mbps", Map.of("type", "integer")));

    @Test
    void validResponseYieldsVerdictTypeErrorsAndParams() {
        llmClient.payload = "{\"semantic_verdict\":true,\"negotiation_type\":\"information\","
                + "\"errors\":[],\"params\":{\"confirmed_rate_mbps\":2}}";

        SemanticValidationResult result = validator.validate(VALID_PROMPT, callerSchema, informationReference);

        assertTrue(result.verdict());
        assertEquals("information", result.negotiationType());
        assertEquals(List.of(), result.errors());
        assertEquals(Map.of("confirmed_rate_mbps", 2), result.params());
        assertEquals(1, llmClient.invocations);
        assertEquals(callerSchema, schemaBuilder.lastCallerSchema);
    }

    @Test
    void singleStructuredCallReceivesMergedSchemaAndFilledUserPrompt() {
        llmClient.payload =
                "{\"semantic_verdict\":true,\"negotiation_type\":\"information\"," + "\"errors\":[],\"params\":{}}";

        validator.validate(VALID_PROMPT, callerSchema, informationReference);

        List<Map<String, String>> messages = llmClient.lastMessages;
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role"));
        assertTrue(messages.get(0).get("content").contains("semantic validation"));
        assertEquals("user", messages.get(1).get("role"));
        String userPrompt = messages.get(1).get("content");
        assertTrue(userPrompt.contains("information"));
        assertTrue(userPrompt.contains(StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri()));
        assertTrue(userPrompt.contains("confirmed_rate_mbps"));
        assertTrue(userPrompt.contains("energy saving region"));
        assertEquals(Map.of("merged", true), llmClient.lastSchema);
        assertNull(llmClient.lastTemperature);
        assertNull(llmClient.lastMaxTokens);
    }

    @Test
    void missingNegotiationTypeKeyIsAShapeViolation() {
        llmClient.payload = "{\"semantic_verdict\":true,\"errors\":[],\"params\":{}}";

        NegotiationValidationException exception = assertThrows(
                NegotiationValidationException.class,
                () -> validator.validate(VALID_PROMPT, callerSchema, informationReference));

        assertTrue(exception.getMessage().contains("negotiation_type"));
    }

    @Test
    void missingOtherRequiredKeysAreShapeViolations() {
        llmClient.payload = "{\"negotiation_type\":\"information\",\"errors\":[],\"params\":{}}";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validate(VALID_PROMPT, callerSchema, informationReference));

        llmClient.payload = "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"params\":{}}";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validate(VALID_PROMPT, callerSchema, informationReference));

        llmClient.payload = "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[]}";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validate(VALID_PROMPT, callerSchema, informationReference));
    }

    @Test
    void wrongShapesAreShapeViolations() {
        llmClient.payload =
                "{\"semantic_verdict\":\"yes\",\"negotiation_type\":\"information\"," + "\"errors\":[],\"params\":{}}";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validate(VALID_PROMPT, callerSchema, informationReference));

        llmClient.payload = "{\"semantic_verdict\":true,\"negotiation_type\":\"information\","
                + "\"errors\":\"none\",\"params\":{}}";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validate(VALID_PROMPT, callerSchema, informationReference));

        llmClient.payload =
                "{\"semantic_verdict\":true,\"negotiation_type\":\"information\"," + "\"errors\":[],\"params\":[]}";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validate(VALID_PROMPT, callerSchema, informationReference));

        llmClient.payload = "{\"semantic_verdict\":true,\"negotiation_type\":\"information\","
                + "\"errors\":[{\"slot_name\":\"section.context\"}],\"params\":{}}";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validate(VALID_PROMPT, callerSchema, informationReference));

        llmClient.payload = "not json at all";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validate(VALID_PROMPT, callerSchema, informationReference));
    }

    @Test
    void nullTypeWithTrueVerdictIsTurnedIntoSemanticRejection() {
        llmClient.payload = "{\"semantic_verdict\":true,\"negotiation_type\":null,\"errors\":[],\"params\":{}}";

        SemanticValidationResult result = validator.validate(VALID_PROMPT, callerSchema, informationReference);

        assertFalse(result.verdict());
        assertNull(result.negotiationType());
        assertEquals(1, result.errors().size());
        assertEquals("section.info_static", result.errors().get(0).slotName());
        assertEquals("template_type_mismatch", result.errors().get(0).code());
    }

    @Test
    void typeMismatchWithTrueVerdictIsTurnedIntoSemanticRejection() {
        llmClient.payload =
                "{\"semantic_verdict\":true,\"negotiation_type\":\"target\"," + "\"errors\":[],\"params\":{}}";

        SemanticValidationResult result = validator.validate(VALID_PROMPT, callerSchema, informationReference);

        assertFalse(result.verdict());
        assertEquals("target", result.negotiationType());
        assertEquals(1, result.errors().size());
        assertEquals("section.target", result.errors().get(0).slotName());
        assertEquals("template_type_mismatch", result.errors().get(0).code());
        assertTrue(result.errors().get(0).message().contains("information"));
    }

    @Test
    void negativeVerdictPassesThroughWithTypeNullAndErrors() {
        llmClient.payload = "{\"semantic_verdict\":false,\"negotiation_type\":null,"
                + "\"errors\":[{\"slot_name\":\"section.target_result_content\","
                + "\"code\":\"conclusion_content_mismatch\",\"message\":\"Mismatch\"}],\"params\":{}}";

        SemanticValidationResult result = validator.validate(VALID_PROMPT, callerSchema, informationReference);

        assertFalse(result.verdict());
        assertNull(result.negotiationType());
        assertEquals(1, result.errors().size());
        assertEquals("section.target_result_content", result.errors().get(0).slotName());
    }

    @Test
    void llmProviderFailureIsWrappedAsInternalValidationException() {
        llmClient.failure = new LLMRuntimeError("invocation failed");

        NegotiationValidationException exception = assertThrows(
                NegotiationValidationException.class,
                () -> validator.validate(VALID_PROMPT, callerSchema, informationReference));

        assertTrue(exception.getMessage().contains("invocation failed"));
    }

    @Test
    void missingPromptResourceForLanguageSurfacesResourceNotFound() {
        NegotiationReference unsupportedLanguageReference =
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPhase.PROPOSE, "fr-FR");

        assertThrows(
                ResourceNotFoundException.class,
                () -> validator.validate(VALID_PROMPT, callerSchema, unsupportedLanguageReference));
        assertEquals(0, llmClient.invocations);
    }

    private static final class RecordingLLMClient implements LLMClient {

        private String payload = "{}";

        private RuntimeException failure;

        private int invocations;

        private List<Map<String, String>> lastMessages;

        private Map<String, Object> lastSchema;

        private Double lastTemperature;

        private Integer lastMaxTokens;

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            invocations++;
            lastMessages = messages;
            lastSchema = jsonSchema;
            lastTemperature = temperature;
            lastMaxTokens = maxTokens;
            if (failure != null) {
                throw failure;
            }
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }

    private static final class RecordingSchemaBuilder implements SemanticSchemaBuilder {

        private Map<String, Object> lastCallerSchema;

        @Override
        public Map<String, Object> buildSemanticValidationSchema(Map<String, Object> callerSchema) {
            lastCallerSchema = callerSchema;
            return Map.of("merged", true);
        }
    }
}
