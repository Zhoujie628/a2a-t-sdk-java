package net.openan.a2at.sdk.corpus.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.generation.NegotiationContentService;
import net.openan.a2at.sdk.corpus.LlmFailMarker;
import net.openan.a2at.sdk.corpus.ScriptedNegotiationLlmClient;

/**
 * Error-code partition property layer (design §8.3): the public error-code universe partitions into exactly the seven
 * negotiation codes, and the retryable partition is exactly the two LLM-step codes.
 *
 * <p>Every trigger runs with the attempt limit fixed at {@link PropertyHarness#MAX_ATTEMPTS}, so the retryable
 * partition has an operational definition observable from the outside: a failure code is retryable if and only if the
 * scripted LLM client was consumed to the attempt limit. Pre-LLM failures (0 calls) and non-retryable in-step failures
 * (1 call) never reach the limit.
 *
 * @since 2026-08
 */
class ErrorCodePartitionPropertyTest {

    /** The complete public negotiation error-code set of the content layer. */
    private static final Set<String> NEGOTIATION_ERROR_CODES = Set.of(
            A2ATErrorCodes.TEMPLATE_NOT_FOUND,
            A2ATErrorCodes.NEGOTIATION_CONTENT_EXTRACT_FAILED,
            A2ATErrorCodes.NEGOTIATION_SEMANTIC_REJECTED,
            A2ATErrorCodes.NEGOTIATION_RULE_VIOLATION,
            A2ATErrorCodes.NEGOTIATION_SLOT_MISSING,
            A2ATErrorCodes.NEGOTIATION_INVALID_INPUT,
            A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR);

    /** The retryable partition: exactly the two LLM-step codes of the design document. */
    private static final Set<String> RETRYABLE_ERROR_CODES = Set.of(
            A2ATErrorCodes.NEGOTIATION_CONTENT_EXTRACT_FAILED,
            A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR);

    @Property(tries = 100, seed = "20260920")
    void everyFailureCarriesACodeFromTheSevenCodeSet(
            @ForAll("languages") String language,
            @ForAll("contexts") NegotiationContext context,
            @ForAll("failureTriggers") FailureTrigger trigger) {
        FailureOutcome outcome = trigger.run(language, context);
        assertTrue(
                NEGOTIATION_ERROR_CODES.contains(outcome.code()),
                "code '" + outcome.code() + "' is outside the seven negotiation error codes");
        assertEquals(trigger.expectedCode(), outcome.code());
        assertEquals(
                RETRYABLE_ERROR_CODES.contains(outcome.code()),
                outcome.llmCalls() == PropertyHarness.MAX_ATTEMPTS,
                "a failure is retried to the attempt limit if and only if its code is retryable, but code "
                        + outcome.code() + " made " + outcome.llmCalls() + " LLM call(s)");
    }

    /** The normalized outcome of one triggered failure: its public error code and its exact LLM call count. */
    private record FailureOutcome(String code, int llmCalls) {}

    /** One deterministic trigger of one public failure code. */
    private enum FailureTrigger {
        BLANK_TEXT {
            @Override
            String expectedCode() {
                return A2ATErrorCodes.NEGOTIATION_INVALID_INPUT;
            }

            @Override
            FailureOutcome run(String language, NegotiationContext context) {
                ScriptedNegotiationLlmClient llm = ScriptedNegotiationLlmClient.assertionOnly();
                NegotiationContentService service = PropertyHarness.service(language, llm);
                A2ATError error = assertThrows(
                        NegotiationGenerationException.class,
                        () -> service.generateProposeFromText(
                                "   ",
                                context,
                                PropertyHarness.templateUri("Negotiation-T/information-negotiation/propose/v1")));
                return new FailureOutcome(error.getCode(), llm.callCount());
            }
        },
        CONCLUSION_MISMATCH {
            @Override
            String expectedCode() {
                return A2ATErrorCodes.NEGOTIATION_INVALID_INPUT;
            }

            @Override
            FailureOutcome run(String language, NegotiationContext context) {
                ScriptedNegotiationLlmClient llm = PropertyHarness.scripted(
                        "{\"conclusion\":\"Reject\",\"confirmed_intent\":null,\"failure_reason\":\"no agreement\"}");
                NegotiationContentService service = PropertyHarness.service(language, llm);
                A2ATError error = assertThrows(
                        NegotiationGenerationException.class,
                        () -> service.generateAcceptFromText(
                                "I must refuse the current offer.",
                                context,
                                PropertyHarness.templateUri("Negotiation-T/target-negotiation/accept-reject/v1")));
                return new FailureOutcome(error.getCode(), llm.callCount());
            }
        },
        MISSING_REQUIRED_FIELD {
            @Override
            String expectedCode() {
                return A2ATErrorCodes.NEGOTIATION_SLOT_MISSING;
            }

            @Override
            FailureOutcome run(String language, NegotiationContext context) {
                ScriptedNegotiationLlmClient llm = PropertyHarness.scripted("{\"relationship\":null}");
                NegotiationContentService service = PropertyHarness.service(language, llm);
                A2ATError error = assertThrows(
                        NegotiationGenerationException.class,
                        () -> service.generateProposeFromText(
                                "Please provide the missing information.",
                                context,
                                PropertyHarness.templateUri("Negotiation-T/information-negotiation/propose/v1")));
                return new FailureOutcome(error.getCode(), llm.callCount());
            }
        },
        ROUND_ABOVE_BUDGET {
            @Override
            String expectedCode() {
                return A2ATErrorCodes.NEGOTIATION_RULE_VIOLATION;
            }

            @Override
            FailureOutcome run(String language, NegotiationContext context) {
                NegotiationContext overBudget =
                        new NegotiationContext(context.id(), context.maxRounds() + 1, context.maxRounds());
                ScriptedNegotiationLlmClient llm = ScriptedNegotiationLlmClient.assertionOnly();
                NegotiationContentService service = PropertyHarness.service(language, llm);
                A2ATError error = assertThrows(
                        NegotiationParamExtractionException.class,
                        () -> service.validateProposePromptAndDataFilling(
                                "Rendered negotiation message text.",
                                overBudget,
                                PropertyHarness.objectSchema(Map.of()),
                                PropertyHarness.templateUri("Negotiation-T/information-negotiation/propose/v1")));
                return new FailureOutcome(error.getCode(), llm.callCount());
            }
        },
        SEMANTIC_REJECTED {
            @Override
            String expectedCode() {
                return A2ATErrorCodes.NEGOTIATION_SEMANTIC_REJECTED;
            }

            @Override
            FailureOutcome run(String language, NegotiationContext context) {
                ScriptedNegotiationLlmClient llm = PropertyHarness.scripted(
                        "{\"semantic_verdict\":false,\"negotiation_type\":\"information\","
                                + "\"errors\":[{\"slot_name\":\"region\",\"code\":\"missing\","
                                + "\"message\":\"The region parameter is missing.\"}],\"params\":{}}");
                NegotiationContentService service = PropertyHarness.service(language, llm);
                A2ATError error = assertThrows(
                        NegotiationParamExtractionException.class,
                        () -> service.validateProposePromptAndDataFilling(
                                "Rendered negotiation message text.",
                                context,
                                PropertyHarness.objectSchema(Map.of()),
                                PropertyHarness.templateUri("Negotiation-T/information-negotiation/propose/v1")));
                return new FailureOutcome(error.getCode(), llm.callCount());
            }
        },
        EXTRACT_FAILED {
            @Override
            String expectedCode() {
                return A2ATErrorCodes.NEGOTIATION_CONTENT_EXTRACT_FAILED;
            }

            @Override
            FailureOutcome run(String language, NegotiationContext context) {
                ScriptedNegotiationLlmClient llm = PropertyHarness.failing(LlmFailMarker.NON_JSON);
                NegotiationContentService service = PropertyHarness.service(language, llm);
                A2ATError error = assertThrows(
                        NegotiationGenerationException.class,
                        () -> service.generateProposeFromText(
                                "Please provide the missing information.",
                                context,
                                PropertyHarness.templateUri("Negotiation-T/information-negotiation/propose/v1")));
                return new FailureOutcome(error.getCode(), llm.callCount());
            }
        },
        LLM_INFRASTRUCTURE_ERROR {
            @Override
            String expectedCode() {
                return A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR;
            }

            @Override
            FailureOutcome run(String language, NegotiationContext context) {
                ScriptedNegotiationLlmClient llm = PropertyHarness.failing(LlmFailMarker.RUNTIME_EXCEPTION);
                NegotiationContentService service = PropertyHarness.service(language, llm);
                A2ATError error = assertThrows(
                        NegotiationGenerationException.class,
                        () -> service.generateProposeFromText(
                                "Please provide the missing information.",
                                context,
                                PropertyHarness.templateUri("Negotiation-T/information-negotiation/propose/v1")));
                return new FailureOutcome(error.getCode(), llm.callCount());
            }
        },
        TEMPLATE_NOT_FOUND {
            @Override
            String expectedCode() {
                return A2ATErrorCodes.TEMPLATE_NOT_FOUND;
            }

            @Override
            FailureOutcome run(String language, NegotiationContext context) {
                ScriptedNegotiationLlmClient llm = ScriptedNegotiationLlmClient.assertionOnly();
                NegotiationContentService service =
                        PropertyHarness.serviceWithFailingTemplateLoader(language, llm);
                A2ATError error = assertThrows(
                        NegotiationGenerationException.class,
                        () -> service.generateProposeFromText(
                                "Please provide the missing information.",
                                context,
                                PropertyHarness.templateUri("Negotiation-T/information-negotiation/propose/v1")));
                return new FailureOutcome(error.getCode(), llm.callCount());
            }
        };

        abstract String expectedCode();

        abstract FailureOutcome run(String language, NegotiationContext context);
    }

    // ------------------------------------------------------------------ providers

    @Provide
    Arbitrary<String> languages() {
        return PropertyArbitraries.languages();
    }

    @Provide
    Arbitrary<NegotiationContext> contexts() {
        return PropertyArbitraries.contexts();
    }

    @Provide
    Arbitrary<FailureTrigger> failureTriggers() {
        return Arbitraries.of(FailureTrigger.values());
    }
}
