package net.openan.a2at.sample.authz_policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.openan.a2at.sample.authz_policy.AuthzScenario.AuthzExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.SlotErrorExpectation;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioOutcome;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.PromptGenerationException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import org.junit.jupiter.api.Test;

class AuthzScenarioRunnerTest {

    private static final TemplateUri TEMPLATE_URI = StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT;
    private static final Map<String, Object> SLOT_SCHEMA = Map.of("properties", Map.of(), "required", Map.of());
    private static final AuthzExpected SUCCESS = new AuthzExpected("success", null, null, null);
    private static final AuthzExpected EXPECTED_SLOT_ERROR =
            new AuthzExpected("slot_validation_error", null, null, null);
    private static final AuthzExpected EXPECTED_SEMANTIC_REJECTED =
            new AuthzExpected("validation_semantic_rejected", null, null, null);

    @Test
    void should_dispatchToFromTextGenerator() {
        AtomicReference<String> calledEntry = new AtomicReference<>();
        AuthzPromptGenerator generator = scenario -> {
            calledEntry.set(scenario.entry());
            return new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertEquals("from_text", calledEntry.get());
    }

    @Test
    void should_dispatchToFromDataWithSchemaGenerator() {
        AtomicReference<String> calledEntry = new AtomicReference<>();
        AuthzPromptGenerator generator = scenario -> {
            calledEntry.set(scenario.entry());
            return new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario(
                "test", "from_data_with_schema", Map.of("data", Map.of("k", "v"), "schema", Map.of("k", "d")), SUCCESS);

        runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertEquals("from_data_with_schema", calledEntry.get());
    }

    @Test
    void should_match_WhenExpectedSuccessAndValidationPasses() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of("k", "v"));
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertEquals("success", outcome.result().outcome());
        assertNotNull(outcome.metadata());
        assertNotNull(outcome.filled());
    }

    @Test
    void should_match_WhenExpectedSemanticRejectedAndSemanticRejected() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            throw new ContentValidationException(A2ATErrorCodes.VALIDATION_SEMANTIC_REJECTED, "semantic rejected");
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario =
                new AuthzScenario("test", "from_text", Map.of("text", "hello"), EXPECTED_SEMANTIC_REJECTED);

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertEquals("validation_semantic_rejected", outcome.result().outcome());
        assertNotNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_notMatch_WhenExpectedSuccessAndValidationRejected() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            throw new ContentValidationException(A2ATErrorCodes.VALIDATION_SEMANTIC_REJECTED, "semantic rejected");
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("validation_semantic_rejected", outcome.result().outcome());
        assertNotNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_notMatch_WhenExpectedSemanticRejectedAndValidationPasses() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of("k", "v"));
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario =
                new AuthzScenario("test", "from_text", Map.of("text", "hello"), EXPECTED_SEMANTIC_REJECTED);

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("success", outcome.result().outcome());
        assertNotNull(outcome.metadata());
        assertNotNull(outcome.filled());
    }

    @Test
    void should_notMatch_WhenExpectedSuccessAndGeneratorThrowsPromptGenerationException() {
        AuthzPromptGenerator generator = scenario -> {
            throw new PromptGenerationException("slot_schema_not_found", "schema not found");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("slot_schema_not_found", outcome.result().outcome());
        assertNotNull(outcome.result().error());
        assertNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_match_WhenExpectedSlotValidationErrorAndGeneratorThrowsSlotValidationError() {
        AuthzPromptGenerator generator = scenario -> {
            throw new PromptGenerationException(
                    A2ATErrorCodes.SLOT_VALIDATION_ERROR, "Required slots are missing or empty: operation_type");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario =
                new AuthzScenario("test", "from_text", Map.of("text", "hello"), EXPECTED_SLOT_ERROR);

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertEquals("slot_validation_error", outcome.result().outcome());
        assertNotNull(outcome.result().error());
        assertNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_notMatch_WhenExpectedSlotValidationErrorAndGeneratorThrowsInfrastructureError() {
        AuthzPromptGenerator generator = scenario -> {
            throw new PromptGenerationException(
                    A2ATErrorCodes.LLM_INVOCATION_FAILED, "LLM invocation failed");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario =
                new AuthzScenario("test", "from_text", Map.of("text", "hello"), EXPECTED_SLOT_ERROR);

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("llm_invocation_failed", outcome.result().outcome());
        assertNotNull(outcome.result().error());
        assertNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_notMatch_WhenExpectedSuccessAndValidatorThrowsInfrastructureError() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR, "infrastructure error");
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("validation_llm_infrastructure_error", outcome.result().outcome());
        assertNotNull(outcome.result().error());
        assertNotNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_notMatch_WhenExpectedSuccessAndValidatorThrowsResourceNotFound() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_PROMPT_RESOURCE_NOT_FOUND, "resource not found");
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("validation_prompt_resource_not_found", outcome.result().outcome());
        assertNotNull(outcome.result().error());
        assertNotNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_notMatch_WhenExpectedSuccessAndValidatorThrowsUnknownCode() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            throw new ContentValidationException("unknown_code", "unknown error");
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("unknown_code", outcome.result().outcome());
        assertNotNull(outcome.result().error());
        assertNotNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_matchSlotErrors_WhenExpectedSlotErrorsMatchActual() {
        AuthzPromptGenerator generator = scenario -> {
            throw new PromptGenerationException(
                    A2ATErrorCodes.SLOT_VALIDATION_ERROR,
                    "Required slots are missing or empty: 授权策略的操作类型",
                    List.of(new SlotValidationError("授权策略的操作类型", "missing_required", "Required slot is missing or empty")));
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzExpected expected = new AuthzExpected(
                "slot_validation_error",
                null,
                null,
                List.of(new SlotErrorExpectation("授权策略的操作类型", "missing_required")));
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), expected);

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertEquals(1, outcome.result().slotErrors().size());
        assertEquals("授权策略的操作类型", outcome.result().slotErrors().get(0).slotName());
        assertEquals("missing_required", outcome.result().slotErrors().get(0).code());
    }

    @Test
    void should_notMatchSlotErrors_WhenExpectedSlotErrorsDontMatchActual() {
        AuthzPromptGenerator generator = scenario -> {
            throw new PromptGenerationException(
                    A2ATErrorCodes.SLOT_VALIDATION_ERROR,
                    "Required slots are missing or empty: 授权策略的操作类型",
                    List.of(new SlotValidationError("授权策略的操作类型", "missing_required", "Required slot is missing or empty")));
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzExpected expected = new AuthzExpected(
                "slot_validation_error",
                null,
                null,
                List.of(new SlotErrorExpectation("动网操作的授权策略列表", "missing_required")));
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), expected);

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("slot_validation_error", outcome.result().outcome());
    }

    @Test
    void should_notMatchSlotErrors_WhenSlotNameMatchesButCodeDiffers() {
        AuthzPromptGenerator generator = scenario -> {
            throw new PromptGenerationException(
                    A2ATErrorCodes.SLOT_VALIDATION_ERROR,
                    "Required slots are missing or empty: 授权策略的操作类型",
                    List.of(new SlotValidationError("授权策略的操作类型", "missing_required", "Required slot is missing or empty")));
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzExpected expected = new AuthzExpected(
                "slot_validation_error",
                null,
                null,
                List.of(new SlotErrorExpectation("授权策略的操作类型", "invalid_value")));
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), expected);

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
    }

    @Test
    void should_notMatch_WhenExpectedSuccessAndGeneratorThrowsRuntimeException() {
        AuthzPromptGenerator generator = scenario -> {
            throw new NullPointerException("unexpected NPE");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("sdk_internal_error", outcome.result().outcome());
        assertNotNull(outcome.result().error());
        assertNull(outcome.metadata());
        assertNull(outcome.filled());
    }
}
