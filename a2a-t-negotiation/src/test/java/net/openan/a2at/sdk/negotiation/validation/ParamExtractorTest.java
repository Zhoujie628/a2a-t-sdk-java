package net.openan.a2at.sdk.negotiation.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import org.junit.jupiter.api.Test;

class ParamExtractorTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String VALID_ZH_PROMPT = "## 协商上下文\n"
            + "- id: " + SESSION_ID + "\n"
            + "- round: 2\n"
            + "- maxRounds: 5\n\n"
            + "## 所需信息项\n"
            + "1. 节能区域信息：请提供真实存在的区域\n";

    private static final NegotiationReference REFERENCE =
            new NegotiationReference(NegotiationType.INFORMATION, NegotiationPhase.PROPOSE, "zh-CN");

    private static final int MAX_ATTEMPTS = 1;

    private final StubComplianceChecker complianceChecker = new StubComplianceChecker();

    private final StubSemanticValidator semanticValidator = new StubSemanticValidator();

    private final NegotiationRuleCheckerAdapter ruleChecker =
            new NegotiationRuleCheckerAdapter(complianceChecker, Vocabulary.forLanguage("zh-CN"));

    private final ParamExtractor extractor = new ParamExtractor(ruleChecker, semanticValidator, MAX_ATTEMPTS);

    @Test
    void happyPathMergesContextParamsFirstAndLetsContextWinOnConflict() {
        complianceChecker.result =
                new NegotiationRuleCheckResult(true, true, List.of(), new NegotiationContext(SESSION_ID, 2, 5));
        semanticValidator.result = new SemanticValidationResult(
                true,
                "information",
                List.of(),
                Map.of("id", "llm-value", "confirmed_rate_mbps", 2, "nested", Map.of("a", 1)));

        FilledParamData filled = extractor.extract(VALID_ZH_PROMPT, Map.of(), REFERENCE);

        assertEquals(SESSION_ID, filled.data().get("id"));
        assertEquals(2, filled.data().get("round"));
        assertEquals(5, filled.data().get("maxRounds"));
        assertEquals(2, filled.data().get("confirmed_rate_mbps"));
        assertEquals(Map.of("a", 1), filled.data().get("nested"));
        assertEquals(5, filled.data().size());
        assertEquals(1, semanticValidator.invocations);
    }

    @Test
    void extractRequiresIntegerRoundAndMaxRoundsInMergedData() {
        complianceChecker.result =
                new NegotiationRuleCheckResult(true, true, List.of(), new NegotiationContext(SESSION_ID, 3, 7));
        semanticValidator.result = new SemanticValidationResult(true, "information", List.of(), Map.of());

        FilledParamData filled = extractor.extract(VALID_ZH_PROMPT, Map.of(), REFERENCE);

        assertTrue(filled.data().get("round") instanceof Integer);
        assertTrue(filled.data().get("maxRounds") instanceof Integer);
        assertEquals(3, filled.data().get("round"));
        assertEquals(7, filled.data().get("maxRounds"));
    }

    @Test
    void ruleFailureSkipsTheSemanticValidationCall() {
        complianceChecker.result = new NegotiationRuleCheckResult(
                false,
                true,
                List.of(new SlotValidationError("round", "out_of_range", "round exceeds maxRounds")),
                null);

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> extractor.extract(VALID_ZH_PROMPT, Map.of(), REFERENCE));

        assertEquals("negotiation_rule_violation", exception.getCode());
        assertEquals(1, exception.getErrors().size());
        assertEquals("round", exception.getErrors().get(0).slotName());
        assertEquals(0, semanticValidator.invocations);
    }

    @Test
    void extractFailsOnRuleViolationWithoutTouchingTheValidator() {
        complianceChecker.result = new NegotiationRuleCheckResult(
                false, true, List.of(new SlotValidationError("id", "invalid_uuid", "not a uuid")), null);

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> extractor.extract(VALID_ZH_PROMPT, Map.of(), REFERENCE));

        assertEquals("negotiation_rule_violation", exception.getCode());
        assertEquals("id", exception.getErrors().get(0).slotName());
        assertEquals(0, semanticValidator.invocations);
    }

    @Test
    void nonNegotiationInputFailsWithLanguageNeutralMessage() {
        complianceChecker.result = new NegotiationRuleCheckResult(false, false, List.of(), null);

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> extractor.extract("## 任务目标\n诊断\n", Map.of(), REFERENCE));

        assertEquals("negotiation_invalid_input", exception.getCode());
        assertEquals(
                "missing negotiation context section; for Task-T compliance use checkTaskPrompt",
                exception.getMessage());
        assertFalse(exception.getMessage().contains("协商上下文"));
        assertEquals(List.of(), exception.getErrors());
        assertEquals(0, semanticValidator.invocations);
    }

    @Test
    void semanticRejectionPassesErrorsThrough() {
        complianceChecker.result =
                new NegotiationRuleCheckResult(true, true, List.of(), new NegotiationContext(SESSION_ID, 2, 5));
        List<SlotValidationError> semanticErrors = List.of(
                new SlotValidationError("section.target_result_content", "conclusion_content_mismatch", "Mismatch"),
                new SlotValidationError("section.context", "invalid_conclusion", "Abort is reserved"));
        semanticValidator.result = new SemanticValidationResult(false, "target", semanticErrors, Map.of());

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> extractor.extract(VALID_ZH_PROMPT, Map.of(), REFERENCE));

        assertEquals("negotiation_semantic_rejected", exception.getCode());
        assertEquals(semanticErrors, exception.getErrors());
    }

    @Test
    void extractReturnsTheValidatorOutcome() {
        complianceChecker.result =
                new NegotiationRuleCheckResult(true, true, List.of(), new NegotiationContext(SESSION_ID, 2, 5));
        semanticValidator.result =
                new SemanticValidationResult(true, "information", List.of(), Map.of("confirmed_rate_mbps", 2));

        FilledParamData filled = extractor.extract(VALID_ZH_PROMPT, Map.of(), REFERENCE);

        assertEquals(1, semanticValidator.invocations);
        assertEquals(2, filled.data().get("confirmed_rate_mbps"));
    }

    @Test
    void internalValidationFailureIsMappedToRetryableInfrastructureError() {
        complianceChecker.result =
                new NegotiationRuleCheckResult(true, true, List.of(), new NegotiationContext(SESSION_ID, 2, 5));
        semanticValidator.failure = new NegotiationValidationException("response is missing negotiation_type");

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> extractor.extract(VALID_ZH_PROMPT, Map.of(), REFERENCE));

        assertEquals("negotiation_llm_infrastructure_error", exception.getCode());
        assertEquals(1, exception.getErrors().size());
        assertEquals("_llm", exception.getErrors().get(0).slotName());
        assertTrue(exception.getMessage().contains("negotiation_type"));
    }

    @Test
    void promptResourceMissIsMappedToTemplateNotFound() {
        complianceChecker.result =
                new NegotiationRuleCheckResult(true, true, List.of(), new NegotiationContext(SESSION_ID, 2, 5));
        semanticValidator.failure =
                new ResourceNotFoundException("prompt resource missing", "prompt_resources/prompts/x");

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> extractor.extract(VALID_ZH_PROMPT, Map.of(), REFERENCE));

        assertEquals("template_not_found", exception.getCode());
        assertEquals(List.of(), exception.getErrors());
    }

    private static final class StubComplianceChecker implements NegotiationComplianceChecker {

        private NegotiationRuleCheckResult result = new NegotiationRuleCheckResult(false, false, List.of(), null);

        @Override
        public NegotiationRuleCheckResult check(String prompt, Vocabulary vocabulary) {
            return result;
        }
    }

    private static final class StubSemanticValidator implements NegotiationSemanticValidator {

        private SemanticValidationResult result = new SemanticValidationResult(false, null, List.of(), Map.of());

        private RuntimeException failure;

        private int invocations;

        @Override
        public SemanticValidationResult validate(
                String prompt, Map<String, Object> callerSchema, NegotiationReference reference) {
            invocations++;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
