package net.openan.a2at.sdk.negotiation.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.validation.StandardTemplates;
import net.openan.a2at.sdk.core.validation.TemplateUri;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMError;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.InfoProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import org.junit.jupiter.api.Test;

/**
 * Verifies the single-root failure contract of the negotiation content layer.
 *
 * <p>Every runtime failure of the generation and parameter-extraction pipelines is catchable through the common
 * {@link A2ATError} root, while programming errors of the content layer (standard {@link IllegalArgumentException}
 * and {@link NullPointerException} of argument validation) stay outside that tree so that callers cannot accidentally
 * swallow them with a generic processing-failure handler.
 */
class NegotiationExceptionTreeRootCatchTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    @Test
    void exhaustedGenerationLlmFailureIsCatchableThroughTheCommonRoot() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new FailingClient())
                .maxAttempts(2)
                .build();

        RuntimeException failure = catchThroughRoot(() -> orchestrator.generateProposeFromText(
                "请提供节能区域。", new NegotiationContext(UUID, 1, 5), INFORMATION_PROPOSE_URI));

        assertTrue(failure instanceof NegotiationGenerationException);
        assertEquals(
                A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR,
                ((NegotiationGenerationException) failure).getCode());
    }

    @Test
    void ruleViolationFailureIsCatchableThroughTheCommonRoot() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .build();

        RuntimeException failure = catchThroughRoot(() -> orchestrator.validateAndFillingProposeData(
                "## 协商上下文\n- id: " + UUID + "\n- round: 9\n- maxRounds: 5",
                Map.of("type", "object"),
                INFORMATION_PROPOSE_URI));

        assertTrue(failure instanceof NegotiationParamExtractionException);
        assertEquals(
                A2ATErrorCodes.NEGOTIATION_RULE_VIOLATION, ((NegotiationParamExtractionException) failure).getCode());
    }

    @Test
    void semanticRejectionFailureIsCatchableThroughTheCommonRoot() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient(
                        "{\"semantic_verdict\":false,\"negotiation_type\":null,\"errors\":[{\"slot_name\":"
                                + "\"section.info_static\",\"code\":\"template_type_mismatch\",\"message\":"
                                + "\"inconsistent\"}],\"params\":{}}"))
                .build();

        RuntimeException failure = catchThroughRoot(() -> orchestrator.validateAndFillingProposeData(
                "## 协商上下文\n- id: " + UUID + "\n- round: 1\n- maxRounds: 5",
                Map.of("type", "object"),
                INFORMATION_PROPOSE_URI));

        assertTrue(failure instanceof NegotiationParamExtractionException);
        assertEquals(
                A2ATErrorCodes.NEGOTIATION_SEMANTIC_REJECTED,
                ((NegotiationParamExtractionException) failure).getCode());
    }

    @Test
    void paramExtractionLlmFailureIsCatchableThroughTheCommonRoot() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new FailingClient())
                .maxAttempts(2)
                .build();

        RuntimeException failure = catchThroughRoot(() -> orchestrator.validateAndFillingProposeData(
                "## 协商上下文\n- id: " + UUID + "\n- round: 1\n- maxRounds: 5",
                Map.of("type", "object"),
                INFORMATION_PROPOSE_URI));

        assertTrue(failure instanceof NegotiationParamExtractionException);
        assertEquals(
                A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR,
                ((NegotiationParamExtractionException) failure).getCode());
    }

    @Test
    void nonNegotiationInputFailureIsCatchableThroughTheCommonRoot() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .build();

        RuntimeException failure = catchThroughRoot(() -> orchestrator.validateAndFillingProposeData(
                "plain text without any negotiation section", Map.of("type", "object"), INFORMATION_PROPOSE_URI));

        assertTrue(failure instanceof NegotiationParamExtractionException);
        assertEquals(
                A2ATErrorCodes.NEGOTIATION_INVALID_INPUT, ((NegotiationParamExtractionException) failure).getCode());
    }

    @Test
    void contentProgrammingErrorsAreNotCatchableThroughTheCommonRoot() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .build();

        try {
            orchestrator.generateProposeFromData(
                    new NegotiationProposeData(
                            new NegotiationContext(UUID, 1, 5),
                            new InfoProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                    StandardTemplates.ENERGY_SAVING);
        } catch (A2ATError caughtByRoot) {
            fail("content programming errors must stay outside the A2ATError tree but were caught: "
                    + caughtByRoot.getMessage());
        } catch (IllegalArgumentException expected) {
            assertFalse(
                    A2ATError.class.isInstance(expected),
                    "content programming errors must stay outside the A2ATError tree");
            assertTrue(
                    expected.getMessage().contains("Template URI does not address"),
                    "the failure must point at the template URI but was: " + expected.getMessage());
        }
    }

    @Test
    void contentProgrammingErrorsOfTheContextStayOutsideTheCommonRoot() {
        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> new NegotiationContext("  ", 1, 5));

        assertFalse(
                A2ATError.class.isInstance(failure), "content programming errors must stay outside the A2ATError tree");
    }

    @Test
    void nullPromptOnValidateAndFillingStaysOutsideTheCommonRoot() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .build();

        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> orchestrator.validateAndFillingProposeData(null, Map.of("type", "object"), INFORMATION_PROPOSE_URI));

        assertFalse(A2ATError.class.isInstance(failure), "a null prompt is a programming error and must stay outside the A2ATError tree");
    }

    @Test
    void blankPromptOnValidateAndFillingStaysOutsideTheCommonRoot() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .build();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.validateAndFillingProposeData(
                        "   ", Map.of("type", "object"), INFORMATION_PROPOSE_URI));

        assertFalse(
                A2ATError.class.isInstance(failure),
                "a blank prompt is a programming error and must stay outside the A2ATError tree");
        assertTrue(
                failure.getMessage().contains("blank"),
                "the failure must point at the blank prompt but was: " + failure.getMessage());
    }

    /**
     * Runs one pipeline action inside a {@code catch (A2ATError)} block and returns the caught failure, failing when
     * nothing was caught.
     */
    private static RuntimeException catchThroughRoot(Runnable action) {
        try {
            action.run();
        } catch (A2ATError failure) {
            return failure;
        }
        fail("the pipeline action was expected to fail with an A2ATError but completed");
        return null;
    }

    private static final class ScriptedClient implements LLMClient {

        private final String payload;

        private ScriptedClient(String payload) {
            this.payload = payload;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }

    private static final class FailingClient implements LLMClient {

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            throw new LLMError("LLM endpoint unavailable.");
        }
    }
}
