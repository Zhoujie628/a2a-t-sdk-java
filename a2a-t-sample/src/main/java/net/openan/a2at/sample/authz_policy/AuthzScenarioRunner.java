package net.openan.a2at.sample.authz_policy;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sample.authz_policy.AuthzScenario.AuthzExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.ClientExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.ServerExpected;
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
 * comparing the staged client/server expectations against the actual behaviour.
 *
 * <p>Assertion semantics mirror the Python probe: client {@code promptText} is compared with
 * trailing-whitespace-insensitive equality; server {@code slot_errors} are subset-matched
 * (each expected error must appear with the same slot name and code); server {@code params}
 * are subset-matched recursively (maps by key, ordered lists element-wise, trimmed strings)
 * and only asserted for expected server outcome {@code success}.
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

    public ScenarioOutcome run(AuthzScenario scenario, Map<String, Object> paramSchema, TemplateUri templateUri) {
        MetadataContent metadata;
        try {
            metadata = generator.generate(scenario);
        } catch (A2ATError e) {
            return buildClientErrorOutcome(scenario, e, null);
        } catch (RuntimeException e) {
            A2ATError wrapped = new A2ATError(A2ATErrorCodes.SDK_INTERNAL_ERROR, e.getMessage(), e);
            return buildClientErrorOutcome(scenario, wrapped, null);
        }

        ClientExpected expectedClient = scenario.expected().client();
        boolean clientPromptMatch = expectedClient.outcome() == null
                && promptTextMatch(expectedClient.promptText(), metadata.promptText());
        if (expectedClient.outcome() != null || !clientPromptMatch) {
            // generation unexpectedly succeeded (or prompt text differs) — no server-stage expectation applies
            return new ScenarioOutcome(
                    new ScenarioResult(
                            AuthzScenario.EXPECTED_SUCCESS,
                            false,
                            null,
                            List.of(),
                            clientPromptMatch,
                            null,
                            null),
                    metadata,
                    null);
        }

        FilledParamData filled;
        try {
            filled = validator.validate(metadata.promptText(), paramSchema, templateUri);
        } catch (A2ATError e) {
            return buildServerErrorOutcome(scenario, e, metadata, clientPromptMatch);
        } catch (RuntimeException e) {
            A2ATError wrapped = new A2ATError(A2ATErrorCodes.SDK_INTERNAL_ERROR, e.getMessage(), e);
            return buildServerErrorOutcome(scenario, wrapped, metadata, clientPromptMatch);
        }

        ServerExpected expectedServer = scenario.expected().server();
        List<SlotValidationError> noErrors = List.of();
        boolean serverOutcomeMatch =
                expectedServer != null && AuthzScenario.EXPECTED_SUCCESS.equals(expectedServer.outcome());
        boolean serverParamsMatch =
                serverOutcomeMatch
                        && (expectedServer.params() == null || paramsMatch(expectedServer.params(), filled.data()));
        boolean match = serverOutcomeMatch && serverParamsMatch;
        return new ScenarioOutcome(
                new ScenarioResult(
                        AuthzScenario.EXPECTED_SUCCESS,
                        match,
                        null,
                        noErrors,
                        clientPromptMatch,
                        serverOutcomeMatch,
                        serverParamsMatch),
                metadata,
                filled);
    }

    private ScenarioOutcome buildClientErrorOutcome(
            AuthzScenario scenario, A2ATError error, MetadataContent metadata) {
        String actualOutcome = error.getCode();
        List<SlotValidationError> actualSlotErrors = extractSlotErrors(error);
        ClientExpected expectedClient = scenario.expected().client();
        boolean outcomeMatch = expectedClient.outcome() != null && expectedClient.outcome().equals(actualOutcome);
        boolean slotErrorsMatch = outcomeMatch && slotErrorsMatch(expectedClient.slotErrors(), actualSlotErrors);
        boolean match = outcomeMatch && slotErrorsMatch;
        return new ScenarioOutcome(
                new ScenarioResult(actualOutcome, match, error, actualSlotErrors, null, null, null), metadata, null);
    }

    private ScenarioOutcome buildServerErrorOutcome(
            AuthzScenario scenario, A2ATError error, MetadataContent metadata, boolean clientPromptMatch) {
        String actualOutcome = error.getCode();
        List<SlotValidationError> actualSlotErrors = extractSlotErrors(error);
        ServerExpected expectedServer = scenario.expected().server();
        boolean outcomeMatch = expectedServer != null && expectedServer.outcome() != null
                && expectedServer.outcome().equals(actualOutcome);
        boolean slotErrorsMatch = outcomeMatch && slotErrorsMatch(expectedServer.slotErrors(), actualSlotErrors);
        boolean match = clientPromptMatch && outcomeMatch && slotErrorsMatch;
        return new ScenarioOutcome(
                new ScenarioResult(
                        actualOutcome, match, error, actualSlotErrors, clientPromptMatch, outcomeMatch, null),
                metadata,
                null);
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

    /** Trailing-whitespace-insensitive equality for the rendered prompt text. */
    static boolean promptTextMatch(String expected, String actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        return expected.strip().equals(actual.strip());
    }

    /** Subset match: every expected slot error must appear in the actual errors with the same name and code. */
    static boolean slotErrorsMatch(List<SlotErrorExpectation> expected, List<SlotValidationError> actual) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        for (SlotErrorExpectation expectedError : expected) {
            boolean found = actual.stream()
                    .anyMatch(actualError ->
                            actualError.slotName().equals(expectedError.slotName())
                                    && actualError.code().equals(expectedError.code()));
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * Recursive subset match for extracted parameters: maps match when every expected key is present with a
     * recursively-equal value; lists match when of equal length and element-wise recursively equal; strings
     * match after trimming; other values match via {@link Objects#equals}.
     */
    static boolean paramsMatch(Object expected, Object actual) {
        if (expected instanceof Map<?, ?> expectedMap) {
            if (!(actual instanceof Map<?, ?> actualMap)) {
                return false;
            }
            for (Map.Entry<?, ?> entry : expectedMap.entrySet()) {
                if (!actualMap.containsKey(entry.getKey())) {
                    return false;
                }
                if (!paramsMatch(entry.getValue(), actualMap.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (expected instanceof List<?> expectedList) {
            if (!(actual instanceof List<?> actualList) || expectedList.size() != actualList.size()) {
                return false;
            }
            for (int i = 0; i < expectedList.size(); i++) {
                if (!paramsMatch(expectedList.get(i), actualList.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (expected instanceof String expectedText) {
            return actual instanceof String actualText && expectedText.strip().equals(actualText.strip());
        }
        return Objects.equals(expected, actual);
    }

    /**
     * Result of one scenario run.
     *
     * @param outcome actual outcome code ({@code success} or an {@code A2ATErrorCodes} value)
     * @param match whether the actual behaviour satisfies all staged expectations
     * @param error the raised error, if any
     * @param slotErrors actual per-slot error details
     * @param clientPromptMatch whether the client-generated prompt text matched; {@code null} when the
     *     client stage was expected to fail
     * @param serverOutcomeMatch whether the server outcome matched; {@code null} when no server stage ran
     * @param serverParamsMatch whether the extracted parameters matched; {@code null} when not asserted
     */
    public record ScenarioResult(
            String outcome,
            boolean match,
            A2ATError error,
            List<SlotValidationError> slotErrors,
            Boolean clientPromptMatch,
            Boolean serverOutcomeMatch,
            Boolean serverParamsMatch) {}

    public record ScenarioOutcome(ScenarioResult result, MetadataContent metadata, FilledParamData filled) {}
}
