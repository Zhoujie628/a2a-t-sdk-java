package net.openan.a2at.sample.authz_policy;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.authz_policy.AuthzScenario.AuthzExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.SlotErrorExpectation;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.PromptGenerationException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidationException;

/**
 * Scenario execution engine that runs a single scenario through prompt generation and validation,
 * comparing the actual outcome against the expected outcome and per-slot error details.
 *
 * @since 2026-08
 */
public final class AuthzScenarioRunner {

    private final AuthzPromptGenerator generator;
    private final AuthzPromptValidator validator;

    public AuthzScenarioRunner(AuthzPromptGenerator generator, AuthzPromptValidator validator) {
        this.generator = generator;
        this.validator = validator;
    }

    public ScenarioOutcome run(AuthzScenario scenario, Map<String, Object> slotSchemaMap, TemplateUri templateUri) {
        MetadataContent metadata;
        try {
            metadata = generator.generate(scenario);
        } catch (A2ATError e) {
            return buildErrorOutcome(scenario, e, null);
        } catch (RuntimeException e) {
            A2ATError wrapped = new A2ATError(A2ATErrorCodes.SDK_INTERNAL_ERROR, e.getMessage(), e);
            return buildErrorOutcome(scenario, wrapped, null);
        }

        FilledParamData filled;
        try {
            filled = validator.validate(metadata.promptText(), slotSchemaMap, templateUri);
        } catch (A2ATError e) {
            return buildErrorOutcome(scenario, e, metadata);
        } catch (RuntimeException e) {
            A2ATError wrapped = new A2ATError(A2ATErrorCodes.SDK_INTERNAL_ERROR, e.getMessage(), e);
            return buildErrorOutcome(scenario, wrapped, metadata);
        }

        String actualOutcome = AuthzScenario.EXPECTED_SUCCESS;
        List<SlotValidationError> actualSlotErrors = List.of();
        boolean match = matchesExpected(scenario.expected(), actualOutcome, actualSlotErrors);
        return new ScenarioOutcome(
                new ScenarioResult(actualOutcome, match, null, actualSlotErrors), metadata, filled);
    }

    private static ScenarioOutcome buildErrorOutcome(
            AuthzScenario scenario, A2ATError error, MetadataContent metadata) {
        String actualOutcome = error.getCode();
        List<SlotValidationError> actualSlotErrors = extractSlotErrors(error);
        boolean match = matchesExpected(scenario.expected(), actualOutcome, actualSlotErrors);
        return new ScenarioOutcome(
                new ScenarioResult(actualOutcome, match, error, actualSlotErrors), metadata, null);
    }

    private static List<SlotValidationError> extractSlotErrors(A2ATError error) {
        if (error instanceof PromptGenerationException pge) {
            return pge.failedParameters();
        }
        if (error instanceof ContentValidationException cve) {
            return cve.errors();
        }
        return List.of();
    }

    private static boolean matchesExpected(
            AuthzExpected expected,
            String actualOutcome,
            List<SlotValidationError> actualSlotErrors) {
        if (!expected.outcome().equals(actualOutcome)) {
            return false;
        }
        List<SlotErrorExpectation> expectedSlotErrors = expected.slotErrors();
        if (expectedSlotErrors == null || expectedSlotErrors.isEmpty()) {
            return true;
        }
        for (SlotErrorExpectation expectedError : expectedSlotErrors) {
            boolean found = actualSlotErrors.stream()
                    .anyMatch(actual ->
                            actual.slotName().equals(expectedError.slotName())
                                    && actual.code().equals(expectedError.code()));
            if (!found) {
                return false;
            }
        }
        return true;
    }

    public record ScenarioResult(
            String outcome, boolean match, A2ATError error, List<SlotValidationError> slotErrors) {}

    public record ScenarioOutcome(ScenarioResult result, MetadataContent metadata, FilledParamData filled) {}
}
