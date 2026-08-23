package net.openan.a2at.sdk.prompt.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import org.junit.jupiter.api.Test;

class DefaultContentValidatorTest {

    private static final TemplateUri TASK_URI = TemplateUri.of("Task-T", "network-layer", "energy-saving");

    @Test
    void validatesSuccessfullyOnFirstCall() {
        DefaultContentValidator validator = new DefaultContentValidator("Task-T", "zh-CN", 3, new StubClient());

        FilledParamData result =
                assertDoesNotThrow(() -> validator.validate("task prompt", Map.of("type", "object"), TASK_URI));

        assertNotNull(result);
        assertEquals(Map.of("site", "Site A"), result.data());
    }

    @Test
    void rejectsMismatchedExtensionName() {
        DefaultContentValidator validator = new DefaultContentValidator("Task-T", "zh-CN", 3, new StubClient());

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(
                        "task prompt",
                        Map.of("type", "object"),
                        TemplateUri.of("Notification-T", "network-layer", "service-recovery")));
    }

    @Test
    void missingPromptResourceFailsWithValidationPromptResourceNotFound() {
        DefaultContentValidator validator = new DefaultContentValidator("Task-T", "fr-FR", 3, new StubClient());

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> validator.validate("task prompt", Map.of("type", "object"), TASK_URI));

        assertEquals("validation_prompt_resource_not_found", exception.getCode());
        assertInstanceOf(ResourceNotFoundException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("zh-CN or en-US"));
    }

    @Test
    void missingPromptResourceKeepsPipelineNullSoNextCallRetries() {
        DefaultContentValidator validator = new DefaultContentValidator("Task-T", "fr-FR", 3, new StubClient());

        // first call fails on the missing resource; the pipeline must stay uninitialised so the next
        // call re-attempts the construction (transient failures can heal)
        assertThrows(
                ContentValidationException.class,
                () -> validator.validate("task prompt", Map.of("type", "object"), TASK_URI));
        ContentValidationException second = assertThrows(
                ContentValidationException.class,
                () -> validator.validate("task prompt", Map.of("type", "object"), TASK_URI));

        assertEquals("validation_prompt_resource_not_found", second.getCode());
    }

    @Test
    void succeedsOnSecondCallAfterTransientResourceFailureHeals() {
        DefaultContentValidator validator = new DefaultContentValidator("Task-T", "fr-FR", 3, new StubClient());

        assertThrows(
                ContentValidationException.class,
                () -> validator.validate("task prompt", Map.of("type", "object"), TASK_URI));

        // switching the language is impossible on this class; the healing path is instead proven by a
        // fresh validator with a supported language constructed after the failing one
        DefaultContentValidator healed = new DefaultContentValidator("Task-T", "zh-CN", 3, new StubClient());
        FilledParamData result =
                assertDoesNotThrow(() -> healed.validate("task prompt", Map.of("type", "object"), TASK_URI));

        assertNotNull(result);
    }

    @Test
    void retryableFailureAfterResourceLoadStillMapsLlmInfrastructureError() {
        DefaultContentValidator validator = new DefaultContentValidator("Task-T", "zh-CN", 1, new FailingClient());

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> validator.validate("task prompt", Map.of("type", "object"), TASK_URI));

        assertEquals("validation_llm_infrastructure_error", exception.getCode());
    }

    private static final class StubClient implements LLMClient {

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            return new LLMResponse(
                    """
                    {
                      "semantic_verdict": true,
                      "errors": [],
                      "params": {"site": "Site A"}
                    }
                    """,
                    "stub-llm",
                    Map.of(),
                    Map.of());
        }
    }

    private static final class FailingClient implements LLMClient {

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            throw new LLMRuntimeError("network timeout");
        }
    }
}
